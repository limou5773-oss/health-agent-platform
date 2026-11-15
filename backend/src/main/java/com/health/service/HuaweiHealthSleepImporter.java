package com.health.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.health.api.ApiException;
import com.health.model.Models.ImportBatch;
import com.health.model.Models.ImportRow;
import com.health.service.SleepImportIntervals.Interval;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class HuaweiHealthSleepImporter {
  private static final String WAKE = "PROFESSIONAL_SLEEP_WAKE";
  private static final Map<String, String> STAGES = Map.of(
      "PROFESSIONAL_SLEEP_DEEP", "深睡", "PROFESSIONAL_SLEEP_SHALLOW", "浅睡",
      "PROFESSIONAL_SLEEP_DREAM", "REM", "PROFESSIONAL_SLEEP_NOON", "午睡");
  private final ObjectReader json;

  public HuaweiHealthSleepImporter(ObjectMapper json) {
    this.json = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  public ImportBatch parse(MultipartFile file, ZoneId zone) {
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_HUAWEI_JSON", "华为运动健康睡眠 JSON 不能为空且不能超过10MB");
    JsonNode records;
    try (var input = file.getInputStream()) {
      records = json.readTree(input);
    } catch (IOException e) {
      throw new ApiException(400, "INVALID_HUAWEI_JSON", "无法读取华为运动健康 JSON 文件");
    }
    if (records == null || !records.isArray())
      throw new ApiException(400, "INVALID_HUAWEI_JSON", "请选择包含专业睡眠记录的 JSON 数组文件");

    ImportBatch batch = new ImportBatch();
    batch.format = "HUAWEI_HEALTH";
    batch.rows = new ArrayList<>();
    List<Interval> intervals = new ArrayList<>();
    String device = null;
    String source = null;
    boolean found = false;
    int pointsRead = 0;
    for (JsonNode record : records) {
      if (!record.path("type").isIntegralNumber() || record.path("type").asLong() != 9) {
        batch.totalRows++;
        batch.skippedRows++;
        continue;
      }
      found = true;
      var points = record.path("samplePoints");
      var code = record.path("deviceCode").asText("").strip();
      if (!points.isArray() || points.isEmpty() || code.isEmpty()) {
        batch.totalRows++;
        batch.invalidRows++;
        ImportRow row = new ImportRow();
        row.rowNumber = batch.totalRows;
        row.error = "专业睡眠记录缺少分期明细或设备编号";
        batch.rows.add(row);
        if (batch.rows.size() > 10000) throw rowLimit();
        continue;
      }
      if (device == null) {
        device = code;
        source = "华为运动健康 · " + UUID.nameUUIDFromBytes(code.getBytes(StandardCharsets.UTF_8));
      } else if (!device.equals(code)) {
        throw new ApiException(400, "HUAWEI_MULTIPLE_DEVICES", "此文件含多个睡眠设备，请上传单个设备的专业睡眠明细");
      }
      for (JsonNode point : points) {
        if (++pointsRead > 10000) throw rowLimit();
        batch.totalRows++;
        readPoint(point, source, zone, batch, intervals);
        if (batch.rows.size() > 10000) throw rowLimit();
      }
    }
    if (!found) throw new ApiException(400, "HUAWEI_SLEEP_MISSING", "文件不含 type=9 的专业睡眠明细，请选择含分期区间的文件");
    SleepImportIntervals.normalizeEpisodes(intervals, zone, batch);
    return batch;
  }

  private static void readPoint(JsonNode point, String source, ZoneId zone, ImportBatch batch,
      List<Interval> intervals) {
    String key = point.path("key").asText("");
    String stage = STAGES.get(key);
    boolean awake = WAKE.equals(key);
    ImportRow row = new ImportRow();
    row.rowNumber = batch.totalRows;
    if (stage == null && !awake) {
      row.error = "未识别的华为睡眠分期";
      batch.unsupportedRows++;
      batch.rows.add(row);
      return;
    }
    var start = point.path("startTime");
    var end = point.path("endTime");
    long startMillis = start.asLong(), endMillis = end.asLong();
    if (!start.isIntegralNumber() || !start.canConvertToLong() || !end.isIntegralNumber()
        || !end.canConvertToLong() || startMillis < 1_000_000_000_000L || endMillis <= startMillis || endMillis - startMillis < 1000) {
      row.error = "睡眠起止时间须为有效的 Unix 毫秒时间，区间至少1秒";
    } else {
      var absoluteStart = Instant.ofEpochMilli(startMillis);
      var absoluteEnd = Instant.ofEpochMilli(endMillis);
      var localStart = absoluteStart.atZone(zone).toLocalDateTime();
      var localEnd = absoluteEnd.atZone(zone).toLocalDateTime();
      if (localEnd.getYear() > 9999) {
        row.error = "睡眠时间超出可记录范围";
      } else {
        intervals.add(new Interval(absoluteStart, absoluteEnd, source, awake ? null : row));
        if (awake) {
          batch.skippedRows++;
        } else {
          row.startTime = localStart.toString();
          row.endTime = localEnd.toString();
          row.sourceName = source;
          row.note = "华为运动健康 · " + stage;
          row.valid = true;
          batch.validRows++;
          batch.rows.add(row);
        }
        return;
      }
    }
    batch.invalidRows++;
    batch.rows.add(row);
  }

  private static ApiException rowLimit() {
    return new ApiException(400, "HUAWEI_ROW_LIMIT", "单次最多处理10000条华为睡眠明细");
  }
}
