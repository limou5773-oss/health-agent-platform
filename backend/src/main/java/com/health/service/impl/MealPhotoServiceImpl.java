package com.health.service.impl;

import com.health.api.ApiException;
import com.health.service.AccountService;
import com.health.service.MealPhotoService;
import com.health.service.SystemSettingsService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MealPhotoServiceImpl implements MealPhotoService {
  private final AccountService accounts;
  private final SystemSettingsService settings;
  private final RestClient agent;

  public MealPhotoServiceImpl(AccountService accounts, SystemSettingsService settings,
      @Value("${app.agent-url}") String url, @Value("${app.internal-service-key}") String key) {
    this.accounts = accounts;
    this.settings = settings;
    var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(95));
    agent = RestClient.builder().baseUrl(url).defaultHeader("X-Service-Key", key).requestFactory(factory).build();
  }

  @Override
  public Preview preview(Long userId, MultipartFile photo) {
    var profile = accounts.profile(userId);
    if (profile == null || !profile.aiConsent)
      throw new ApiException(403, "AI_CONSENT_REQUIRED", "请先在个人档案中授权AI分析");
    if (photo.isEmpty() || photo.getSize() > 5 * 1024 * 1024)
      throw new ApiException(400, "INVALID_MEAL_PHOTO", "请选择不超过 5 MB 的 JPEG 或 PNG");

    Encoded image = encode(photo);
    var config = settings.get();
    Preview result;
    try {
      result = agent.post().uri("/internal/model/meal-photo")
          .body(Map.of("mimeType", image.mimeType(), "imageBase64", image.base64(),
              "modelName", config.modelName(), "timeoutSeconds", config.modelTimeoutSeconds()))
          .retrieve().body(Preview.class);
    } catch (RestClientException e) {
      throw new ApiException(502, "MEAL_PHOTO_MODEL_FAILED", "照片识别失败。请确认模型支持图片和结构化输出，或手动录入。");
    }
    if (result == null || result.candidates() == null)
      throw new ApiException(502, "INVALID_AGENT_RESULT", "未收到有效识别结果，请手动录入");
    var current = accounts.profile(userId);
    if (current == null || !current.aiConsent || current.aiConsentVersion != profile.aiConsentVersion)
      throw new ApiException(403, "AI_CONSENT_CHANGED", "AI授权已变化，请重新发起识别");
    return result;
  }

  private record Encoded(String mimeType, String base64) {}

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
          throw new ApiException(400, "INVALID_MEAL_PHOTO", "照片像素总数须在 2500 万以内");
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
      throw new ApiException(400, "INVALID_MEAL_PHOTO", "照片无法读取，请重新选择 JPEG 或 PNG");
    }
  }

  private static ApiException unsupportedPhoto() {
    return new ApiException(400, "INVALID_MEAL_PHOTO", "仅支持 JPEG 或 PNG 照片");
  }
}
