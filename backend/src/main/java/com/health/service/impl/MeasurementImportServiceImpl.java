package com.health.service.impl;

import com.health.api.ApiException;
import com.health.api.HeartRateController;
import com.health.api.WeightRequests;
import com.health.mapper.MeasurementImportMapper;
import com.health.mapper.TaskMapper;
import com.health.model.MeasurementImportModels.*;
import com.health.service.MeasurementImportService;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MeasurementImportServiceImpl implements MeasurementImportService {
  private final MeasurementImportMapper mapper;
  private final TaskMapper tasks;
  private final Validator validator;

  public MeasurementImportServiceImpl(MeasurementImportMapper mapper, TaskMapper tasks, Validator validator) {
    this.mapper = mapper;
    this.tasks = tasks;
    this.validator = validator;
  }

  @Transactional
  public Preview preview(Long userId, Metric metric, MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_CSV", "CSV不能为空且不能超过10MB");

    record MeasurementKey(LocalDateTime measuredAt, String sourceName) {}
    var unique = new LinkedHashMap<MeasurementKey, Row>();
    Batch batch = new Batch();
    batch.userId = userId;
    batch.metric = metric;
    batch.status = "PREVIEW";
    var expectedHeaders = List.of("measuredAt", metric.valueColumn, "sourceName", "note");
    try (var stream = BOMInputStream.builder().setInputStream(file.getInputStream()).get();
         var reader = new InputStreamReader(stream, StandardCharsets.UTF_8.newDecoder());
         var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
             .setAllowMissingColumnNames(true).get().parse(reader)) {
      if (!parser.getHeaderNames().equals(expectedHeaders))
        throw new ApiException(400, "INVALID_CSV_HEADER", "表头须为 " + String.join(",", expectedHeaders));

      for (var csv : parser) {
        if (++batch.totalRows > 10_000) throw new ApiException(400, "TOO_MANY_ROWS", "CSV最多10000条记录");
        long line = csv.getRecordNumber() + 1;
        if (csv.size() != 4) throw invalidRow(line, "列数须为4");
        LocalDateTime measuredAt;
        BigDecimal value;
        try {
          measuredAt = LocalDateTime.parse(csv.get(0)).truncatedTo(ChronoUnit.SECONDS);
          value = new BigDecimal(csv.get(1));
        } catch (DateTimeParseException | NumberFormatException e) {
          throw invalidRow(line, "测量时间或数值格式无效");
        }
        Row row = new Row(line, measuredAt, value, csv.get(2), csv.get(3));
        var errors = switch (metric) {
          case WEIGHT -> validator.validate(new WeightRequests.Measurement(measuredAt, value, row.sourceName(), row.note()));
          case HEART_RATE -> validator.validate(new HeartRateController.Measurement(measuredAt, value, row.sourceName(), row.note()));
        };
        if (!errors.isEmpty()) throw invalidRow(line, errors.iterator().next().getMessage());

        Row previous = unique.putIfAbsent(new MeasurementKey(measuredAt, row.sourceName()), row);
        if (previous != null && previous.value().compareTo(value) != 0)
          throw invalidRow(line, "文件内同来源同一时刻有不同数值");
      }
    } catch (IOException | UncheckedIOException e) {
      throw new ApiException(400, "INVALID_CSV", "无法读取UTF-8 CSV");
    }
    if (unique.isEmpty()) throw new ApiException(400, "INVALID_CSV", "CSV没有数据行");

    var rows = new ArrayList<>(unique.values());
    batch.firstMeasurement = rows.stream().map(Row::measuredAt).min(LocalDateTime::compareTo).orElseThrow();
    batch.lastMeasurement = rows.stream().map(Row::measuredAt).max(LocalDateTime::compareTo).orElseThrow();
    tasks.lockUser(userId);
    mapper.insertBatch(batch);
    for (int start = 0; start < rows.size(); start += 500)
      mapper.insertRows(batch.id, rows.subList(start, Math.min(start + 500, rows.size())));
    rejectConflict(batch);
    batch.duplicateRows = batch.totalRows - rows.size() + mapper.existingRows(batch);
    mapper.updateBatch(batch);
    return new Preview(mapper.batch(userId, metric, batch.id), mapper.sources(batch.id));
  }

  @Transactional(readOnly = true)
  public Preview preview(Long userId, Metric metric, Long id) {
    Batch batch = batch(userId, metric, id);
    return new Preview(batch, mapper.sources(id));
  }

  @Transactional
  public Batch confirm(Long userId, Metric metric, Long id) {
    tasks.lockUser(userId);
    Batch batch = batch(userId, metric, id);
    if ("CONFIRMED".equals(batch.status)) return batch;
    rejectConflict(batch);
    batch.insertedRows = mapper.insertMeasurements(batch);
    batch.duplicateRows = batch.totalRows - batch.insertedRows;
    mapper.confirm(batch);
    mapper.deleteRows(id);
    return mapper.batch(userId, metric, id);
  }

  public List<Batch> history(Long userId, Metric metric) {
    return mapper.history(userId, metric);
  }

  private Batch batch(Long userId, Metric metric, Long id) {
    Batch batch = mapper.batch(userId, metric, id);
    if (batch == null) throw ApiException.missing();
    return batch;
  }

  private void rejectConflict(Batch batch) {
    Long rowNumber = mapper.conflictRow(batch);
    if (rowNumber != null)
      throw ApiException.conflict("第" + rowNumber + "行：同来源同一时刻已有不同数值，请核对记录");
  }

  private static ApiException invalidRow(long line, String reason) {
    return new ApiException(400, "INVALID_CSV_ROW", "第" + line + "行：" + reason);
  }
}
