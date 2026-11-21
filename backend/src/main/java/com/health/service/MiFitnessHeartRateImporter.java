package com.health.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.health.api.ApiException;
import com.health.model.MeasurementImportModels.Row;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MiFitnessHeartRateImporter {
  private static final Set<String> HEART_RATE_KEYS = Set.of("heart_rate", "single_heart_rate");
  private static final Pattern DECIMAL = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");
  private final ObjectReader json;

  public MiFitnessHeartRateImporter(ObjectMapper json) {
    this.json = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  public record Parsed(List<Row> rows, int skippedRows) {}

  public Parsed parse(MultipartFile file, ZoneId zone, String requestedSid) {
    var selected = MiFitnessCsv.select(file, HEART_RATE_KEYS, requestedSid, "心率");
    var rows = new ArrayList<Row>();
    int skipped = selected.skippedRows();
    for (var entry : selected.entries()) {
      if (entry.error() != null) throw invalidRow(entry.number(), entry.error());
      try {
        var value = json.readTree(entry.value());
        var rawBpm = value.path("bpm").asText(value.path("hr").asText());
        if (!DECIMAL.matcher(rawBpm).matches())
          throw invalidRow(entry.number(), "心率须为十进制测量值");
        var bpm = new BigDecimal(rawBpm);
        if (bpm.signum() == 0) {
          skipped++;
          continue;
        }
        var rawTime = value.path("time").asText(value.path("date_time").asText(entry.time()));
        var measuredAt = Instant.ofEpochSecond(Long.parseLong(rawTime)).atZone(zone).toLocalDateTime();
        if (zone.getRules().getValidOffsets(measuredAt).size() != 1)
          throw invalidRow(entry.number(), "档案时区下的时间存在夏令时歧义，请使用 UTC 时区重新导入");
        rows.add(new Row(entry.number(), measuredAt, bpm, selected.sourceName(), ""));
      } catch (JsonProcessingException e) {
        throw invalidRow(entry.number(), "Value 不是有效的心率 JSON");
      } catch (DateTimeException | NumberFormatException e) {
        throw invalidRow(entry.number(), "测量时间须为有效 Unix 秒");
      }
    }
    if (rows.isEmpty())
      throw new ApiException(400, "MI_FITNESS_NO_HEART_RATE", "文件没有可预览的 Mi Fitness 心率明细");
    return new Parsed(rows, skipped);
  }

  private static ApiException invalidRow(long line, String reason) {
    return new ApiException(400, "INVALID_CSV_ROW", "第" + line + "行：" + reason);
  }
}
