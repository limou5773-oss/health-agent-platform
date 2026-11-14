package com.health.service.impl;

import com.health.security.Identity;
import com.health.api.ApiException;
import com.health.api.HeartRateController;
import com.health.api.WeightRequests;
import com.health.mapper.MeasurementImportMapper;
import com.health.mapper.TaskMapper;
import com.health.model.MeasurementImportModels.*;
import com.health.service.MeasurementImportService;
import com.health.service.AccountService;
import com.health.service.AppleHealthExportReader;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
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
  private static final DateTimeFormatter APPLE_TIME =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xx").withResolverStyle(ResolverStyle.STRICT);
  private final MeasurementImportMapper mapper;
  private final TaskMapper tasks;
  private final Validator validator;
  private final AccountService accounts;
  private final AppleHealthExportReader apple;

  public MeasurementImportServiceImpl(MeasurementImportMapper mapper, TaskMapper tasks, Validator validator,
      AccountService accounts, AppleHealthExportReader apple) {
    this.mapper = mapper;
    this.tasks = tasks;
    this.validator = validator;
    this.accounts = accounts;
    this.apple = apple;
  }

  @Transactional
  public Preview preview(Long userId, Metric metric, MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_CSV", "CSV不能为空且不能超过10MB");

    Batch batch = new Batch();
    batch.importFormat = "CSV";
    List<Row> parsed = new ArrayList<>();
    var expectedHeaders = metric.importKind.headers;
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
        parsed.add(new Row(line, measuredAt, value, csv.get(2), csv.get(3)));
      }
    } catch (IOException | UncheckedIOException e) {
      throw new ApiException(400, "INVALID_CSV", "无法读取UTF-8 CSV");
    }
    if (parsed.isEmpty()) throw new ApiException(400, "INVALID_CSV", "CSV没有数据行");
    return savePreview(userId, metric, batch, parsed);
  }

  @Transactional
  public Preview previewApple(Long userId, Metric metric, MultipartFile file) {
    var profile = accounts.profile(userId);
    if (profile == null) throw new ApiException(401, "ACCOUNT_UNAVAILABLE", "账号不可用");
    var zone = ZoneId.of(profile.timezone);
    String type = metric == Metric.WEIGHT ? "HKQuantityTypeIdentifierBodyMass" : "HKQuantityTypeIdentifierHeartRate";
    String unit = metric == Metric.WEIGHT ? "kg" : "count/min";
    Batch batch = new Batch();
    batch.importFormat = "APPLE_HEALTH";
    List<Row> parsed = new ArrayList<>();
    apple.read(file, xml -> {
      if (!type.equals(xml.getAttributeValue(null, "type"))) {
        batch.skippedRows++;
        return;
      }
      if (++batch.totalRows > 10_000)
        throw new ApiException(400, "TOO_MANY_ROWS", "单次最多导入10000条所选指标记录");
      long rowNumber = (long) batch.totalRows + batch.skippedRows;
      var rawTime = xml.getAttributeValue(null, "startDate");
      var rawValue = xml.getAttributeValue(null, "value");
      var source = xml.getAttributeValue(null, "sourceName");
      if (rawTime == null || rawValue == null || source == null || source.isBlank())
        throw appleRow(rowNumber, "缺少测量时间、数值或来源");
      if (rawValue.length() > 64 || !rawValue.matches("[0-9]+(?:\\.[0-9]+)?"))
        throw appleRow(rowNumber, "数值须为十进制测量值");
      String originalUnit = xml.getAttributeValue(null, "unit");
      boolean pounds = metric == Metric.WEIGHT && "lb".equals(originalUnit);
      if (!unit.equals(originalUnit) && !pounds)
        throw appleRow(rowNumber, "不支持测量单位，体重支持 kg 或 lb，心率支持 count/min");
      try {
        var measuredAt = OffsetDateTime.parse(rawTime, APPLE_TIME).atZoneSameInstant(zone).toLocalDateTime();
        if (zone.getRules().getValidOffsets(measuredAt).size() != 1)
          throw appleRow(rowNumber, "档案时区下的时间存在夏令时歧义，请使用 UTC 时区重新导入");
        var value = new BigDecimal(rawValue);
        if (pounds) value = value.multiply(new BigDecimal("0.45359237"));
        if (value.scale() > 3) value = value.setScale(3, RoundingMode.HALF_UP);
        parsed.add(new Row(rowNumber, measuredAt, value, "苹果健康 · " + source,
            "原始测量时间 " + rawTime + "，原值 " + rawValue + " " + originalUnit));
      } catch (DateTimeParseException | NumberFormatException e) {
        throw appleRow(rowNumber, "测量时间或数值格式无效");
      }
    });
    if (parsed.isEmpty()) throw new ApiException(400, "APPLE_METRIC_MISSING", "文件中没有所选指标，已跳过 " + batch.skippedRows + " 条其他记录");
    return savePreview(userId, metric, batch, parsed);
  }

  private Preview savePreview(Long userId, Metric metric, Batch batch, List<Row> parsed) {
    record MeasurementKey(LocalDateTime measuredAt, String sourceName) {}
    var unique = new LinkedHashMap<MeasurementKey, Row>();
    for (Row row : parsed) {
      var errors = switch (metric) {
        case WEIGHT -> validator.validate(new WeightRequests.Measurement(row.measuredAt(), row.value(), row.sourceName(), row.note()));
        case HEART_RATE -> validator.validate(new HeartRateController.Measurement(row.measuredAt(), row.value(), row.sourceName(), row.note()));
      };
      String reason = null;
      if (!errors.isEmpty()) reason = errors.iterator().next().getMessage();
      else if (row.measuredAt().getYear() < 1000 || row.measuredAt().getYear() > 9999) reason = "测量时间超出存储范围";
      else {
        Row previous = unique.putIfAbsent(new MeasurementKey(row.measuredAt(), row.sourceName()), row);
        if (previous != null && previous.value().compareTo(row.value()) != 0)
          reason = "文件内同来源同一时刻有不同数值";
      }
      if (reason != null) throw "CSV".equals(batch.importFormat) ? invalidRow(row.rowNumber(), reason) : appleRow(row.rowNumber(), reason);
    }
    batch.userId = userId;
    batch.metric = metric;
    batch.status = "PREVIEW";
    var rows = new ArrayList<>(unique.values());
    batch.firstMeasurement = rows.stream().map(Row::measuredAt).min(LocalDateTime::compareTo).orElseThrow();
    batch.lastMeasurement = rows.stream().map(Row::measuredAt).max(LocalDateTime::compareTo).orElseThrow();
    Identity.requireExisting(tasks.lockUser(userId));
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
    Identity.requireExisting(tasks.lockUser(userId));
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
      throw new ApiException(409, "MEASUREMENT_CONFLICT", "第" + rowNumber + "行：同来源同一时刻已有不同数值，请核对记录");
  }

  private static ApiException invalidRow(long line, String reason) {
    return new ApiException(400, "INVALID_CSV_ROW", "第" + line + "行：" + reason);
  }

  private static ApiException appleRow(long row, String reason) {
    return new ApiException(400, "INVALID_APPLE_MEASUREMENT", "第" + row + "条 Record：" + reason);
  }
}
