package com.health.service;

import com.health.api.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.stream.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AppleHealthExportReader {
  private static final long UPLOAD_LIMIT = 100L * 1024 * 1024;
  private static final long EXPANDED_LIMIT = 500L * 1024 * 1024;

  @FunctionalInterface
  public interface RecordVisitor {
    void accept(XMLStreamReader record);
  }

  public void read(MultipartFile file, RecordVisitor visitor) {
    if (file.isEmpty() || file.getSize() > UPLOAD_LIMIT) {
      throw new ApiException(400, "INVALID_APPLE_FILE", "苹果健康XML/ZIP文件不能为空且不能超过100MB");
    }
    String filename = file.getOriginalFilename();
    String extension = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (!extension.endsWith(".xml") && !extension.endsWith(".zip")) {
      throw new ApiException(400, "INVALID_APPLE_FILE", "请选择苹果健康导出的XML或ZIP文件");
    }
    try (InputStream input = file.getInputStream()) {
      if (extension.endsWith(".zip")) parseZip(input, visitor);
      else parseXml(input, visitor);
    } catch (IOException | XMLStreamException e) {
      throw new ApiException(400, "INVALID_APPLE_FILE", "无法读取苹果健康文件，请检查XML格式、ZIP结构及文件完整性");
    }
  }

  private void parseZip(InputStream input, RecordVisitor visitor)
      throws IOException, XMLStreamException {
    try (ZipInputStream zip = new ZipInputStream(input)) {
      ExpandedZipStream expanded = new ExpandedZipStream(zip);
      int entryCount = 0;
      boolean found = false;
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
        if (++entryCount > 10000) {
          throw new ApiException(400, "APPLE_ZIP_ENTRY_LIMIT", "ZIP最多包含10000个条目");
        }
        String name = entry.getName();
        if (name.equals("export.xml") || name.equals("apple_health_export/export.xml")) {
          if (found) {
            throw new ApiException(400, "DUPLICATE_APPLE_XML", "ZIP中存在多个export.xml，请保留一份导出文件");
          }
          found = true;
          parseXml(expanded, visitor);
        }
        // Consume every entry through the same byte counter; no paths are extracted to disk.
        expanded.transferTo(OutputStream.nullOutputStream());
        zip.closeEntry();
      }
      if (!found) {
        throw new ApiException(
            400, "APPLE_XML_MISSING", "ZIP中未找到export.xml或apple_health_export/export.xml");
      }
    }
  }

  private void parseXml(InputStream input, RecordVisitor visitor)
      throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
    // Apple exports include an inline DTD. Consume the declaration without resolving its entities.
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    XMLStreamReader xml = factory.createXMLStreamReader(input);
    boolean rootSeen = false;
    try {
      while (xml.hasNext()) {
        if (xml.next() != XMLStreamConstants.START_ELEMENT) continue;
        String element = xml.getLocalName();
        if (!rootSeen) {
          rootSeen = true;
          if (!element.equals("HealthData")) {
            throw new ApiException(400, "INVALID_APPLE_XML", "XML根节点须为苹果健康HealthData");
          }
        }
        if (!element.equals("Record")) continue;
        visitor.accept(xml);
      }
    } finally {
      xml.close();
    }
  }

  /**
   * Counts actual decompressed bytes across all ZIP entries, including unselected metrics/files.
   */
  private static final class ExpandedZipStream extends InputStream {
    private final ZipInputStream zip;
    private long bytes;

    private ExpandedZipStream(ZipInputStream zip) {
      this.zip = zip;
    }

    @Override
    public int read() throws IOException {
      int value = zip.read();
      if (value != -1) count(1);
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int n = zip.read(buffer, offset, length);
      if (n > 0) count(n);
      return n;
    }

    private void count(int n) {
      bytes += n;
      if (bytes > EXPANDED_LIMIT) {
        throw new ApiException(400, "APPLE_ZIP_SIZE_LIMIT", "ZIP实际解压总量不能超过500MB");
      }
    }
  }
}
