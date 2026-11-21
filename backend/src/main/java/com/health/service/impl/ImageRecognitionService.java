package com.health.service.impl;

import com.health.api.ApiException;
import com.health.model.CheckupModels.Preview;
import com.health.service.AccountService;
import com.health.service.SystemSettingsService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageRecognitionService {
  private final AccountService accounts;
  private final SystemSettingsService settings;
  private final RestClient agent;

  public ImageRecognitionService(AccountService accounts, SystemSettingsService settings,
      @Value("${app.agent-url}") String url, @Value("${app.internal-service-key}") String key) {
    this.accounts = accounts;
    this.settings = settings;
    var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(95));
    agent = RestClient.builder().baseUrl(url).defaultHeader("X-Service-Key", key).requestFactory(factory).build();
  }

  public <T> T recognize(Long userId, MultipartFile photo, String endpoint, Class<T> resultType) {
    return recognize(userId, endpoint, resultType, () -> {
      if (photo.isEmpty() || photo.getSize() > 5 * 1024 * 1024)
        throw new ApiException(400, "INVALID_RECOGNITION_IMAGE", "请选择不超过 5 MB 的 JPEG 或 PNG");
      Encoded image = encode(photo);
      return Map.of("mimeType", image.mimeType(), "imageBase64", image.base64());
    });
  }

  public Preview recognizeCheckupPdf(Long userId, MultipartFile file) {
    return recognize(userId, "/internal/model/checkup-pdf", Preview.class,
        () -> Map.of("pageImages", renderPages(file)));
  }

  private <T> T recognize(Long userId, String endpoint, Class<T> resultType,
      Supplier<Map<String, Object>> payload) {
    var profile = accounts.profile(userId);
    if (profile == null || !profile.aiConsent)
      throw new ApiException(403, "AI_CONSENT_REQUIRED", "请先在个人档案中授权AI分析");
    var body = new LinkedHashMap<>(payload.get());
    var config = settings.get();
    body.put("modelName", config.modelName());
    body.put("timeoutSeconds", config.modelTimeoutSeconds());
    T result;
    try {
      result = agent.post().uri(endpoint)
          .body(body)
          .retrieve().body(resultType);
    } catch (RestClientException e) {
      throw new ApiException(502, "IMAGE_MODEL_FAILED", "图片识别失败，请确认模型支持图片和结构化输出");
    }
    if (result == null)
      throw new ApiException(502, "INVALID_AGENT_RESULT", "未收到有效识别结果，请重试或手动录入");
    var current = accounts.profile(userId);
    if (current == null || !current.aiConsent || current.aiConsentVersion != profile.aiConsentVersion)
      throw new ApiException(403, "AI_CONSENT_CHANGED", "AI授权已变化，请重新发起识别");
    return result;
  }

  private record Encoded(String mimeType, String base64) {}

  private static List<String> renderPages(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_CHECKUP_PDF", "请选择不超过 10 MB 的 PDF");
    try (var pdf = Loader.loadPDF(file.getBytes())) {
      if (pdf.isEncrypted())
        throw new ApiException(400, "ENCRYPTED_CHECKUP_PDF", "请选择未加密的体检报告 PDF");
      int pages = pdf.getNumberOfPages();
      if (pages < 1 || pages > 5)
        throw new ApiException(400, "INVALID_CHECKUP_PDF", "每份 PDF 支持 1 至 5 页，请拆分后上传");
      var renderer = new PDFRenderer(pdf);
      renderer.setSubsamplingAllowed(true);
      List<String> images = new ArrayList<>(pages);
      for (int i = 0; i < pages; i++) {
        var crop = pdf.getPage(i).getCropBox();
        float edge = Math.max(crop.getWidth(), crop.getHeight());
        if (!Float.isFinite(edge) || crop.getWidth() <= 0 || crop.getHeight() <= 0)
          throw new ApiException(400, "INVALID_CHECKUP_PDF", "PDF 第 " + (i + 1) + " 页的尺寸无效");
        float scale = 1800f / edge;
        var image = renderer.renderImage(i, scale);
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", bytes)) throw new IllegalStateException("JPEG编码器不可用");
        images.add(Base64.getEncoder().encodeToString(bytes.toByteArray()));
      }
      return images;
    } catch (InvalidPasswordException e) {
      throw new ApiException(400, "ENCRYPTED_CHECKUP_PDF", "请选择未加密的体检报告 PDF");
    } catch (IOException e) {
      throw new ApiException(400, "INVALID_CHECKUP_PDF", "无法读取 PDF，请检查文件或重新导出");
    }
  }

  private static Encoded encode(MultipartFile photo) {
    try (var source = photo.getInputStream(); var input = new MemoryCacheImageInputStream(source)) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw unsupportedPhoto();
      var reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!"jpeg".equals(format) && !"png".equals(format)) throw unsupportedPhoto();
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if ((long) width * height > 25_000_000)
          throw new ApiException(400, "INVALID_RECOGNITION_IMAGE", "照片像素总数须在 2500 万以内");
        int sample = (Math.max(width, height) + 1199) / 1200;
        var params = reader.getDefaultReadParam();
        params.setSourceSubsampling(sample, sample, 0, 0);
        var photoPixels = reader.read(0, params);
        var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
          if (!ImageIO.write(photoPixels, "png", output)) throw new IllegalStateException("PNG编码器不可用");
        }
        return new Encoded("image/png", Base64.getEncoder().encodeToString(bytes.toByteArray()));
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new ApiException(400, "INVALID_RECOGNITION_IMAGE", "照片无法读取，请重新选择 JPEG 或 PNG");
    }
  }

  private static ApiException unsupportedPhoto() {
    return new ApiException(400, "INVALID_RECOGNITION_IMAGE", "仅支持 JPEG 或 PNG 照片");
  }
}
