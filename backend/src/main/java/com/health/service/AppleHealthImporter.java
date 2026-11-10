package com.health.service;

import com.health.api.ApiException;
import com.health.model.Models.ImportBatch;
import com.health.model.Models.ImportRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.stream.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads Apple Health exports; it does not retain other health metrics or write business records.
 */
@Component
public class AppleHealthImporter {
  private static final long UPLOAD_LIMIT = 100L * 1024 * 1024;
  private static final long EXPANDED_LIMIT = 500L * 1024 * 1024;
  private static final String SLEEP_TYPE = "HKCategoryTypeIdentifierSleepAnalysis";
  private static final String SOURCE_PREFIX = "苹果健康 · ";
  private static final Set<String> ASLEEP =
      Set.of(
          "HKCategoryValueSleepAnalysisAsleep",
          "HKCategoryValueSleepAnalysisAsleepUnspecified",
          "HKCategoryValueSleepAnalysisAsleepCore",
          "HKCategoryValueSleepAnalysisAsleepDeep",
          "HKCategoryValueSleepAnalysisAsleepREM");
  private static final Set<String> MARKERS =
      Set.of("HKCategoryValueSleepAnalysisInBed", "HKCategoryValueSleepAnalysisAwake");
  private static final DateTimeFormatter APPLE_TIME =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xx").withResolverStyle(ResolverStyle.STRICT);

  public ImportBatch parse(MultipartFile file, ZoneId profileZone) {
    if (file.isEmpty() || file.getSize() > UPLOAD_LIMIT) {
      throw new ApiException(400, "INVALID_APPLE_FILE", "苹果健康XML/ZIP文件不能为空且不能超过100MB");
    }
    String filename = file.getOriginalFilename();
    String extension = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (!extension.endsWith(".xml") && !extension.endsWith(".zip")) {
      throw new ApiException(400, "INVALID_APPLE_FILE", "请选择苹果健康导出的XML或ZIP文件");
    }
    ImportBatch batch = new ImportBatch();
    batch.format = "APPLE_HEALTH";
    batch.rows = new ArrayList<>();
    List<Interval> intervals = new ArrayList<>();
    try (InputStream input = file.getInputStream()) {
      if (extension.endsWith(".zip")) {
        parseZip(input, profileZone, batch, intervals);
      } else {
        parseXml(input, profileZone, batch, intervals);
      }
    } catch (IOException | XMLStreamException e) {
      throw new ApiException(400, "INVALID_APPLE_FILE", "无法读取苹果健康文件，请检查XML格式、ZIP结构及文件完整性");
    }
    normalizeEpisodes(intervals, profileZone, batch);
    return batch;
  }

  private void parseZip(InputStream input, ZoneId zone, ImportBatch batch, List<Interval> intervals)
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
          parseXml(expanded, zone, batch, intervals);
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

  private void parseXml(InputStream input, ZoneId zone, ImportBatch batch, List<Interval> intervals)
      throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
    // Apple exports include an inline DTD. Consume the declaration without resolving its entities.
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    XMLStreamReader xml = factory.createXMLStreamReader(input);
    boolean rootSeen = false;
    int sleepRecords = 0;
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
        batch.totalRows++;
        if (!SLEEP_TYPE.equals(xml.getAttributeValue(null, "type"))) {
          batch.skippedRows++;
          continue;
        }
        if (++sleepRecords > 10000) {
          throw new ApiException(400, "APPLE_SLEEP_RECORD_LIMIT", "单次最多处理10000条睡眠Record，请缩小导出范围");
        }
        readSleepRecord(xml, zone, batch, intervals);
      }
    } finally {
      xml.close();
    }
  }

  private void readSleepRecord(
      XMLStreamReader xml, ZoneId zone, ImportBatch batch, List<Interval> intervals) {
    String value = xml.getAttributeValue(null, "value");
    ImportRow row = new ImportRow();
    row.rowNumber = batch.totalRows;
    boolean asleep = ASLEEP.contains(value == null ? "" : value);
    boolean marker = MARKERS.contains(value == null ? "" : value);
    if (!asleep && !marker) {
      row.error = "未支持的苹果睡眠阶段，未计入睡眠时长";
      batch.unsupportedRows++;
      batch.rows.add(row);
      return;
    }
    if (marker) batch.skippedRows++;
    String rawStart = xml.getAttributeValue(null, "startDate");
    String rawEnd = xml.getAttributeValue(null, "endDate");
    String source = xml.getAttributeValue(null, "sourceName");
    try {
      if (source == null || source.isBlank() || SOURCE_PREFIX.length() + source.length() > 100) {
        throw new IllegalArgumentException("来源名称缺失或加前缀后超过100字符");
      }
      if (rawStart == null || rawEnd == null || rawStart.length() > 64 || rawEnd.length() > 64) {
        throw new IllegalArgumentException("缺少起止时间或时间字段过长");
      }
      OffsetDateTime start = OffsetDateTime.parse(rawStart, APPLE_TIME);
      OffsetDateTime end = OffsetDateTime.parse(rawEnd, APPLE_TIME);
      Instant absoluteStart = start.toInstant();
      Instant absoluteEnd = end.toInstant();
      LocalDateTime localStart = start.atZoneSameInstant(zone).toLocalDateTime();
      LocalDateTime localEnd = end.atZoneSameInstant(zone).toLocalDateTime();
      if (ChronoUnit.SECONDS.between(absoluteStart, absoluteEnd) < 1
          || localStart.getYear() < 1000
          || localEnd.getYear() > 9999) {
        throw new IllegalArgumentException("睡眠区间须至少1秒且能表示为档案时区的有效本地时间");
      }
      String sourceName = SOURCE_PREFIX + source;
      if (asleep) {
        row.startTime = localStart.toString();
        row.endTime = localEnd.toString();
        row.sourceName = sourceName;
        row.note =
            "Apple Health value=" + value + "; startDate=" + rawStart + "; endDate=" + rawEnd;
        row.valid = true;
        batch.validRows++;
        batch.rows.add(row);
      }
      intervals.add(
          new Interval(absoluteStart, absoluteEnd, sourceName, asleep ? row : null));
    } catch (DateTimeException | IllegalArgumentException e) {
      row.error = marker ? "清醒/卧床标记的来源或时间无效，未用于睡眠日期归属" : "睡眠来源、时间格式或区间无效；需带原始时区偏移且区间至少1秒";
      if (asleep) batch.invalidRows++;
      batch.rows.add(row);
    }
  }

  private void normalizeEpisodes(List<Interval> intervals, ZoneId zone, ImportBatch batch) {
    Map<String, List<Interval>> sources = new HashMap<>();
    for (Interval interval : intervals) {
      sources.computeIfAbsent(interval.source(), ignored -> new ArrayList<>()).add(interval);
    }
    for (List<Interval> source : sources.values()) {
      source.sort(Comparator.comparing(Interval::start).thenComparing(Interval::end));
      int componentStart = 0;
      Instant componentEnd = source.getFirst().end();
      for (int i = 1; i <= source.size(); i++) {
        if (i < source.size() && !source.get(i).start().isAfter(componentEnd)) {
          if (source.get(i).end().isAfter(componentEnd)) componentEnd = source.get(i).end();
          continue;
        }
        LocalDate sleepDate = componentEnd.atZone(zone).toLocalDate();
        for (int j = componentStart; j < i; j++) {
          ImportRow asleep = source.get(j).row();
          if (asleep != null) asleep.sleepDate = sleepDate;
        }
        if (i < source.size()) {
          componentStart = i;
          componentEnd = source.get(i).end();
        }
      }
      Map<LocalDate, List<Interval>> days = new HashMap<>();
      for (Interval interval : source) {
        if (interval.row() != null) {
          days.computeIfAbsent(interval.row().sleepDate, ignored -> new ArrayList<>()).add(interval);
        }
      }
      for (List<Interval> day : days.values()) {
        Instant start = day.getFirst().start();
        Instant end = start;
        for (Interval interval : day) {
          if (interval.end().isAfter(end)) end = interval.end();
        }
        if (zone.getRules().getOffset(start).equals(zone.getRules().getOffset(end))) continue;
        // The existing statistics merge local timestamps for each source and sleep date.
        // Exclude the whole affected group so separate stages cannot overlap after a clock reset.
        for (Interval interval : day) {
          ImportRow row = interval.row();
          row.valid = false;
          row.error = "该来源在此睡眠日期跨越夏令时或UTC偏移切换，当前不支持；请将档案时区改为UTC后重新导入";
          batch.validRows--;
          batch.unsupportedRows++;
        }
      }
    }
  }

  private record Interval(Instant start, Instant end, String source, ImportRow row) {}

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
