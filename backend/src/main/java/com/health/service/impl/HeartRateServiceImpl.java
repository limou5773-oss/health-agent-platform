package com.health.service.impl;

import com.health.api.ApiException;
import com.health.api.HeartRateController.Measurement;
import com.health.api.Requests.StatisticsQuery;
import com.health.mapper.HeartRateMapper;
import com.health.mapper.TaskMapper;
import com.health.model.HeartRateModels.*;
import com.health.model.Models.Page;
import com.health.service.HeartRateService;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HeartRateServiceImpl implements HeartRateService {
  private final HeartRateMapper mapper;
  private final TaskMapper tasks;
  private final Validator validator;

  public HeartRateServiceImpl(HeartRateMapper mapper, TaskMapper tasks, Validator validator) {
    this.mapper = mapper;
    this.tasks = tasks;
    this.validator = validator;
  }

  @Transactional(readOnly = true)
  public Page<Sample> records(Long userId, StatisticsQuery q) {
    return new Page<>(mapper.records(userId, q, (q.page - 1L) * q.pageSize),
        mapper.totals(userId, q).recordCount(), q.page, q.pageSize);
  }

  @Transactional(readOnly = true)
  public Statistics statistics(Long userId, StatisticsQuery q) {
    if ((q.sourceName == null || q.sourceName.isEmpty()) && mapper.hasOverlappingSources(userId, q))
      throw new ApiException(409, "SOURCE_CONFLICT", "同一测量时刻有多个来源，请选择来源后查看心率统计");
    var totals = mapper.totals(userId, q);
    var days = mapper.daily(userId, q).stream().collect(Collectors.toMap(Daily::date, day -> day));
    List<Daily> daily = new ArrayList<>();
    for (LocalDate date = q.startDate; !date.isAfter(q.endDate); date = date.plusDays(1)) {
      daily.add(days.getOrDefault(date, new Daily(date, 0, null)));
    }
    return new Statistics(ChronoUnit.DAYS.between(q.startDate, q.endDate) + 1,
        days.size(), totals.recordCount(), totals.averageBpm(), totals.dataCutoff(), daily);
  }

  public List<String> sources(Long userId) { return mapper.sources(userId); }

  @Transactional
  public Sample save(Long userId, Long id, Measurement measurement) {
    tasks.lockUser(userId);
    Sample sample = sample(userId, measurement);
    if (id == null) {
      mapper.insert(sample);
    } else {
      Sample previous = mapper.record(userId, id);
      if (previous == null) throw ApiException.missing();
      sample.id = id;
      sample.sourceName = previous.sourceName;
      mapper.update(sample);
    }
    return mapper.record(userId, sample.id);
  }

  @Transactional
  public void delete(Long userId, Long id) {
    tasks.lockUser(userId);
    if (mapper.delete(userId, id) == 0) throw ApiException.missing();
  }

  @Transactional
  public ImportResult upload(Long userId, MultipartFile file, boolean confirm) {
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_CSV", "CSV不能为空且不能超过10MB");
    List<Sample> samples = new ArrayList<>();
    try (var reader = new InputStreamReader(file.getInputStream(),
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT));
         var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
             .setAllowMissingColumnNames(true).get().parse(reader)) {
      var headers = parser.getHeaderNames();
      if (headers.size() != 4 || !headers.getFirst().replace("\uFEFF", "").equals("measuredAt")
          || !headers.subList(1, 4).equals(List.of("bpm", "sourceName", "note")))
        throw new ApiException(400, "INVALID_CSV_HEADER", "表头须为 measuredAt,bpm,sourceName,note");
      for (var row : parser) {
        long line = row.getRecordNumber() + 1;
        if (samples.size() >= 10_000) throw new ApiException(400, "TOO_MANY_ROWS", "CSV最多10000条记录");
        if (row.size() != 4) throw invalidRow(line, "列数须为4");
        Measurement input;
        try {
          input = new Measurement(LocalDateTime.parse(row.get(0)), new BigDecimal(row.get(1)), row.get(2), row.get(3));
        } catch (DateTimeParseException | NumberFormatException e) {
          throw invalidRow(line, "测量时间或心率格式无效");
        }
        var errors = validator.validate(input);
        if (!errors.isEmpty()) throw invalidRow(line, errors.iterator().next().getMessage());
        samples.add(sample(userId, input));
      }
    } catch (IOException | UncheckedIOException e) {
      throw new ApiException(400, "INVALID_CSV", "无法读取UTF-8 CSV");
    }
    if (samples.isEmpty()) throw new ApiException(400, "INVALID_CSV", "CSV没有数据行");

    if (confirm) tasks.lockUser(userId);
    record MeasurementKey(LocalDateTime measuredAt, String sourceName) {}
    var pending = new LinkedHashMap<MeasurementKey, Sample>();
    int duplicates = 0;
    for (int i = 0; i < samples.size(); i++) {
      Sample sample = samples.get(i);
      var key = new MeasurementKey(sample.measuredAt, sample.sourceName);
      Sample existing = pending.get(key);
      if (existing == null) existing = mapper.sameMeasurement(sample);
      if (existing == null) {
        pending.put(key, sample);
      } else if (existing.bpm.compareTo(sample.bpm) == 0) {
        duplicates++;
      } else {
        throw invalidRow(i + 2L, "同来源同一时刻已有不同数值，请核对后通过记录页更正");
      }
    }
    if (confirm) pending.values().forEach(mapper::insert);
    return new ImportResult(pending.size(), duplicates,
        samples.stream().map(sample -> sample.measuredAt).min(LocalDateTime::compareTo).orElseThrow(),
        samples.stream().map(sample -> sample.measuredAt).max(LocalDateTime::compareTo).orElseThrow(),
        samples.stream().map(sample -> sample.sourceName).distinct().sorted().toList());
  }

  private static Sample sample(Long userId, Measurement input) {
    Sample sample = new Sample();
    sample.userId = userId;
    sample.measuredAt = input.measuredAt();
    sample.bpm = input.bpm();
    sample.sourceName = input.sourceName();
    sample.note = input.note();
    return sample;
  }

  private static ApiException invalidRow(long line, String reason) {
    return new ApiException(400, "INVALID_CSV_ROW", "第" + line + "行：" + reason);
  }
}
