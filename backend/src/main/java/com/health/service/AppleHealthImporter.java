package com.health.service;

import com.health.api.ApiException;
import com.health.model.Models.ImportBatch;
import com.health.model.Models.ImportRow;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads Apple Health exports; it does not retain other health metrics or write business records.
 */
@Component
public class AppleHealthImporter {
  private final AppleHealthExportReader reader;

  public AppleHealthImporter(AppleHealthExportReader reader) {
    this.reader = reader;
  }
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
    ImportBatch batch = new ImportBatch();
    batch.format = "APPLE_HEALTH";
    batch.rows = new ArrayList<>();
    List<Interval> intervals = new ArrayList<>();
    int[] sleepRecords = {0};
    reader.read(file, xml -> {
      batch.totalRows++;
      if (!SLEEP_TYPE.equals(xml.getAttributeValue(null, "type"))) {
        batch.skippedRows++;
        return;
      }
      if (++sleepRecords[0] > 10000)
        throw new ApiException(400, "APPLE_SLEEP_RECORD_LIMIT", "单次最多处理10000条睡眠Record，请缩小导出范围");
      readSleepRecord(xml, profileZone, batch, intervals);
    });
    normalizeEpisodes(intervals, profileZone, batch);
    return batch;
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
}
