package com.health.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.health.api.ApiException;
import com.health.model.Models.ImportBatch;
import com.health.model.Models.ImportRow;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MiFitnessSleepImporter {
  private static final Set<String> SLEEP_KEYS = Set.of("sleep", "watch_night_sleep", "watch_daytime_sleep");
  private static final List<String> STAGES = List.of("深睡", "浅睡", "REM", "清醒");
  private static final String LIMIT_MESSAGE = "单批最多预览10000条睡眠明细，请缩小上传范围";
  private final ObjectReader json;

  public MiFitnessSleepImporter(ObjectMapper json) {
    this.json = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  public ImportBatch parse(MultipartFile file, ZoneId zone) {
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_MI_FITNESS_CSV", "Mi Fitness 明细 CSV 不能为空且不能超过10MB");
    ImportBatch batch = new ImportBatch();
    batch.format = "MI_FITNESS";
    batch.rows = new ArrayList<>();
    try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(),
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)))) {
      String first = reader.readLine();
      if (first == null || first.isBlank()) throw invalidHeader();
      var header = fields(first.startsWith("\uFEFF") ? first.substring(1) : first).stream()
          .map(name -> name.strip().toLowerCase(Locale.ROOT)).toList();
      int sid = header.indexOf("sid"), key = header.indexOf("key"), value = header.indexOf("value");
      if (sid < 0 || key < 0 || value < 0 || !header.contains("time")) throw invalidHeader();

      String source = null;
      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) continue;
        batch.totalRows++;
        var row = fields(line);
        if (row.size() != header.size()) {
          addIssue(batch, lineNumber, "CSV 列数与表头不一致", false);
          continue;
        }
        if (!SLEEP_KEYS.contains(row.get(key))) {
          batch.skippedRows++;
          continue;
        }
        String deviceId = row.get(sid);
        if (!deviceId.matches("[0-9]{1,32}")) {
          addIssue(batch, lineNumber, "缺少有效设备来源编号 Sid", false);
          continue;
        }
        String rowSource = "Mi Fitness · " + deviceId;
        if (source != null && !source.equals(rowSource))
          throw new ApiException(400, "MI_FITNESS_MULTIPLE_DEVICES", "文件含多个睡眠来源，请按 Sid 分开上传并选择对应设备");
        source = rowSource;
        JsonNode payload;
        try {
          payload = json.readTree(row.get(value));
        } catch (JsonProcessingException e) {
          addIssue(batch, lineNumber, "Value 不是有效的睡眠 JSON", false);
          continue;
        }
        if (!payload.isObject()) {
          addIssue(batch, lineNumber, "Value 缺少睡眠 JSON 对象", false);
          continue;
        }
        var sessions = payload.path("segment_details");
        if (sessions.isArray()) {
          if (sessions.isEmpty()) addIssue(batch, lineNumber, "没有睡眠分期区间", true);
          for (JsonNode session : sessions) addSession(batch, session, source, zone, lineNumber);
        } else {
          addSession(batch, payload, source, zone, lineNumber);
        }
      }
    } catch (IOException e) {
      throw new ApiException(400, "INVALID_MI_FITNESS_CSV", "无法解析 Mi Fitness UTF-8 明细 CSV，请检查导出文件");
    }
    if (batch.rows.isEmpty())
      throw new ApiException(400, "MI_FITNESS_NO_SLEEP", "文件没有可预览的睡眠明细，请选择包含睡眠的明细 CSV");
    return batch;
  }

  private static void addSession(ImportBatch batch, JsonNode session, String source, ZoneId zone, int lineNumber) {
    JsonNode items = session.path("items");
    if (!items.isArray() || items.isEmpty()) {
      addIssue(batch, lineNumber, "没有睡眠分期区间，无法根据汇总时长还原睡眠", true);
      return;
    }
    if (items.size() > 10000) throw new ApiException(400, "MI_FITNESS_ROW_LIMIT", LIMIT_MESSAGE);
    long bedtime = epochSeconds(session.path("bedtime"));
    long wake = epochSeconds(session.path("wake_up_time"));
    if (bedtime == Long.MIN_VALUE || wake <= bedtime) {
      addIssue(batch, lineNumber, "入睡或醒来的 Unix 秒时间无效", false);
      return;
    }
    var localBedtime = Instant.ofEpochSecond(bedtime).atZone(zone);
    var localWake = Instant.ofEpochSecond(wake).atZone(zone);
    if (!localBedtime.getOffset().equals(localWake.getOffset())
        || zone.getRules().getValidOffsets(localBedtime.toLocalDateTime()).size() != 1
        || zone.getRules().getValidOffsets(localWake.toLocalDateTime()).size() != 1) {
      addIssue(batch, lineNumber, "睡眠跨越 UTC 偏移切换或本地时间有歧义，请改用 UTC 档案时区后重新导入", true);
      return;
    }
    if (localBedtime.getYear() < 1000 || localWake.getYear() > 9999) {
      addIssue(batch, lineNumber, "睡眠时间超出档案时区可存储范围", false);
      return;
    }
    List<ImportRow> asleep = new ArrayList<>();
    long previousEnd = bedtime;
    for (JsonNode item : items) {
      JsonNode state = item.path("state");
      int stage = state.isIntegralNumber() && state.canConvertToInt() ? state.intValue() : -1;
      if (stage < 2 || stage > 5) {
        addIssue(batch, lineNumber, "发现未识别的睡眠分期，整段未纳入", true);
        return;
      }
      long start = epochSeconds(item.path("start_time"));
      long end = epochSeconds(item.path("end_time"));
      if (start < previousEnd || end <= start || end > wake) {
        addIssue(batch, lineNumber, "分期时间无效、顺序重叠或超出本次睡眠范围", false);
        return;
      }
      previousEnd = end;
      if (stage == 5) continue;
      ImportRow row = new ImportRow();
      row.startTime = Instant.ofEpochSecond(start).atZone(zone).toLocalDateTime().toString();
      row.endTime = Instant.ofEpochSecond(end).atZone(zone).toLocalDateTime().toString();
      row.sleepDate = localWake.toLocalDate();
      row.sourceName = source;
      row.note = "Mi Fitness · " + STAGES.get(stage - 2);
      row.valid = true;
      asleep.add(row);
    }
    if (asleep.isEmpty()) {
      addIssue(batch, lineNumber, "仅有清醒分期，无可导入睡眠", true);
      return;
    }
    for (ImportRow row : asleep) addRow(batch, row);
    batch.validRows += asleep.size();
  }

  private static long epochSeconds(JsonNode field) {
    if (!field.isIntegralNumber() || !field.canConvertToLong()) return Long.MIN_VALUE;
    long seconds = field.longValue();
    return seconds >= -30610224000L && seconds <= 253402300799L ? seconds : Long.MIN_VALUE;
  }

  private static void addIssue(ImportBatch batch, int lineNumber, String reason, boolean unsupported) {
    ImportRow row = new ImportRow();
    row.error = "CSV 第" + lineNumber + "行：" + reason;
    addRow(batch, row);
    if (unsupported) batch.unsupportedRows++;
    else batch.invalidRows++;
  }

  private static void addRow(ImportBatch batch, ImportRow row) {
    if (batch.rows.size() >= 10000) throw new ApiException(400, "MI_FITNESS_ROW_LIMIT", LIMIT_MESSAGE);
    row.rowNumber = batch.rows.size() + 1;
    batch.rows.add(row);
  }

  private static List<String> fields(String line) throws IOException {
    // Mi Fitness documents seen in exports include both ordinary CSV and an extra pair of quotes around the row.
    List<String> parsed;
    try (var csv = CSVParser.parse(line, CSVFormat.DEFAULT)) {
      parsed = csv.getRecords().getFirst().toList();
    } catch (UncheckedIOException e) {
      if (line.length() < 2 || !line.startsWith("\"") || !line.endsWith("\"")) throw e.getCause();
      parsed = List.of(line.substring(1, line.length() - 1));
    }
    if (parsed.size() != 1 || !parsed.getFirst().contains(",")) return parsed;
    try (var csv = CSVParser.parse(parsed.getFirst(), CSVFormat.DEFAULT)) {
      return csv.getRecords().getFirst().toList();
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static ApiException invalidHeader() {
    return new ApiException(400, "INVALID_MI_FITNESS_HEADER", "请选择解压后含 Sid、Key、Time、Value 的 Mi Fitness 健康明细 CSV");
  }
}
