package com.health.service.impl;

import com.health.api.ApiException;
import com.health.api.Requests;
import com.health.api.WeightRequests;
import com.health.mapper.WeightMapper;
import com.health.model.Models.Page;
import com.health.model.WeightModels.*;
import com.health.service.WeightService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightServiceImpl implements WeightService {
  private final WeightMapper mapper;

  public WeightServiceImpl(WeightMapper mapper) { this.mapper = mapper; }

  public Page<WeightRecord> records(Long uid, Requests.SleepQuery q) {
    return new Page<>(mapper.records(uid, q, (q.page - 1L) * q.pageSize), mapper.recordCount(uid, q), q.page, q.pageSize);
  }

  public WeightRecord record(Long uid, Long id) {
    WeightRecord record = mapper.record(uid, id);
    if (record == null) throw ApiException.missing();
    return record;
  }

  @Transactional
  public WeightRecord createRecord(Long uid, WeightRequests.Measurement request) {
    mapper.lockUser(uid);
    WeightRecord record = measurement(uid, request);
    mapper.insertRecord(record);
    return mapper.record(uid, record.id);
  }

  @Transactional
  public WeightRecord updateRecord(Long uid, Long id, WeightRequests.Measurement request) {
    mapper.lockUser(uid);
    record(uid, id);
    WeightRecord record = measurement(uid, request);
    record.id = id;
    mapper.updateRecord(record);
    return mapper.record(uid, id);
  }

  @Transactional
  public void deleteRecord(Long uid, Long id) {
    mapper.lockUser(uid);
    record(uid, id);
    mapper.deleteRecord(uid, id);
  }

  public List<String> sources(Long uid) { return mapper.sources(uid); }

  private static WeightRecord measurement(Long uid, WeightRequests.Measurement request) {
    WeightRecord record = new WeightRecord();
    record.userId = uid;
    record.measuredAt = request.measuredAt();
    record.weightKg = request.weightKg();
    record.sourceName = request.sourceName();
    record.note = request.note();
    return record;
  }

  public WeightStatistics statistics(Long uid, LocalDate start, LocalDate end, String source) {
    // Mapper ordering makes each day's first record the latest measuredAt/id representative.
    List<WeightRecord> records = mapper.range(uid, start, end, source);
    Map<LocalDate, List<WeightRecord>> days = new HashMap<>();
    Map<LocalDateTime, String> eventSources = new HashMap<>();
    for (WeightRecord record : records) {
      String previous = eventSources.putIfAbsent(record.measuredAt, record.sourceName);
      if (previous != null && !previous.equals(record.sourceName))
        throw new ApiException(409, "SOURCE_SELECTION_REQUIRED", "同秒测量存在不同来源，请先选择来源");
      days.computeIfAbsent(record.measuredAt.toLocalDate(), key -> new ArrayList<>()).add(record);
    }

    List<WeightDaily> daily = new ArrayList<>();
    WeightRecord first = null, last = null;
    BigDecimal total = BigDecimal.ZERO;
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      List<WeightRecord> bucket = days.get(date);
      if (bucket == null) {
        daily.add(new WeightDaily(date, 0, null));
        continue;
      }
      WeightRecord representative = bucket.getFirst();
      daily.add(new WeightDaily(date, bucket.size(), representative));
      total = total.add(representative.weightKg);
      if (first == null) first = representative;
      last = representative;
    }
    long period = ChronoUnit.DAYS.between(start, end) + 1;
    BigDecimal average = days.isEmpty() ? null : total.divide(BigDecimal.valueOf(days.size()), 3, RoundingMode.HALF_UP);
    BigDecimal change = first == null ? null : last.weightKg.subtract(first.weightKg);
    LocalDateTime cutoff = records.isEmpty() ? null : records.getFirst().measuredAt;
    BigDecimal coverage = BigDecimal.valueOf(days.size()).multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
    return new WeightStatistics(start, end, source, period, days.size(), coverage,
        records.size(), average, first, last, change, cutoff, daily);
  }

  public WeightGoal goal(Long uid) { return mapper.goal(uid); }

  @Transactional
  public WeightGoal saveGoal(Long uid, WeightRequests.Goal request) {
    mapper.lockUser(uid);
    WeightGoal goal = new WeightGoal();
    goal.userId = uid;
    goal.startDate = request.startDate();
    goal.targetDate = request.targetDate();
    goal.targetWeightKg = request.targetWeightKg();
    goal.sourceName = request.sourceName();
    mapper.saveGoal(goal);
    return mapper.goal(uid);
  }

  @Transactional
  public void deleteGoal(Long uid) {
    mapper.lockUser(uid);
    mapper.deleteGoal(uid);
  }

  @Transactional(readOnly = true)
  public WeightGoalProgress goalProgress(Long uid) {
    WeightGoal goal = mapper.goal(uid);
    if (goal == null) return null;
    WeightStatistics statistics = statistics(uid, goal.startDate, goal.targetDate, goal.sourceName);
    WeightRecord baseline = statistics.first(), current = statistics.last();
    if (baseline == null)
      return new WeightGoalProgress(goal, statistics.periodDays(), statistics.validDays(), statistics.coverageRate(),
          null, null, null, null, null, null, null, null);

    int targetDirection = goal.targetWeightKg.compareTo(baseline.weightKg);
    String direction = targetDirection < 0 ? "LOSE" : targetDirection > 0 ? "GAIN" : "MAINTAIN";
    BigDecimal difference = current.weightKg.subtract(goal.targetWeightKg);
    BigDecimal progress = targetDirection == 0 ? null : statistics.changeKg().multiply(BigDecimal.valueOf(100))
        .divide(goal.targetWeightKg.subtract(baseline.weightKg), 2, RoundingMode.HALF_UP);
    boolean reached = switch (direction) {
      case "LOSE" -> difference.signum() <= 0;
      case "GAIN" -> difference.signum() >= 0;
      default -> difference.signum() == 0;
    };
    return new WeightGoalProgress(goal, statistics.periodDays(), statistics.validDays(), statistics.coverageRate(),
        baseline, current, statistics.changeKg(), difference, progress, direction, reached, statistics.dataCutoff());
  }

}
