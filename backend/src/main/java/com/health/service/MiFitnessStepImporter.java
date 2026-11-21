package com.health.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.health.api.ApiException;
import com.health.model.ActivityModels.StepImportRow;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MiFitnessStepImporter {
  private final ObjectReader json;

  public MiFitnessStepImporter(ObjectMapper json) {
    this.json = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  public record Parsed(List<StepImportRow> rows, int skippedRows) {}

  public Parsed parse(MultipartFile file, ZoneId zone, String requestedSid) {
    var selected = MiFitnessCsv.select(file, Set.of("steps"), requestedSid, "步数");
    var rows = new ArrayList<StepImportRow>();
    int skipped = selected.skippedRows();
    for (var entry : selected.entries()) {
      StepImportRow row = new StepImportRow();
      row.rowNumber = entry.number();
      row.error = entry.error();
      if (row.error != null) {
        rows.add(row);
        continue;
      }
      try {
        var value = json.readTree(entry.value());
        int steps = Integer.parseInt(value.path("steps").asText());
        if (steps == 0) {
          skipped++;
          continue;
        }
        row.steps = Integer.toString(steps);
        long seconds = Long.parseLong(value.path("time").asText(entry.time()));
        var minute = Instant.ofEpochSecond(seconds);
        var start = minute.atZone(zone);
        var end = minute.plusSeconds(60).atZone(zone);
        row.startTime = start.toLocalDateTime().toString();
        row.endTime = end.toLocalDateTime().toString();
        row.sourceName = selected.sourceName();
        row.mode = "DELTA";
        row.note = "Mi Fitness 分钟步数";
        if (start.getYear() < 1000 || end.getYear() > 9999) {
          row.error = "步数时间超出可保存范围";
        } else if (!start.getOffset().equals(end.getOffset())
            || zone.getRules().getValidOffsets(start.toLocalDateTime()).size() != 1
            || zone.getRules().getValidOffsets(end.toLocalDateTime()).size() != 1) {
          row.error = "档案时区存在偏移切换或时间歧义，请改用 UTC 时区后重新导入";
        }
      } catch (JsonProcessingException e) {
        row.error = "Value 不是有效的步数 JSON";
      } catch (DateTimeException | NumberFormatException e) {
        row.error = "时间须为有效 Unix 秒，步数须为非负整数";
      }
      rows.add(row);
    }
    if (rows.isEmpty())
      throw new ApiException(400, "MI_FITNESS_NO_STEPS", "文件没有可预览的 Mi Fitness 步数明细");
    return new Parsed(rows, skipped);
  }
}
