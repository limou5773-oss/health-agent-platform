package com.health.service.impl;

import com.health.security.Identity;
import com.health.api.ApiException;
import com.health.api.HeartRateController.Measurement;
import com.health.api.Requests.StatisticsQuery;
import com.health.mapper.HeartRateMapper;
import com.health.mapper.PlanMapper;
import com.health.mapper.TaskMapper;
import com.health.model.HeartRateModels.*;
import com.health.model.Models.Page;
import com.health.service.HeartRateService;
import com.health.service.TaskService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HeartRateServiceImpl implements HeartRateService {
  private final HeartRateMapper mapper;
  private final TaskMapper tasks;
  private final PlanMapper reports;
  private final TaskService agentTasks;

  public HeartRateServiceImpl(HeartRateMapper mapper, TaskMapper tasks, PlanMapper reports, TaskService agentTasks) {
    this.mapper = mapper;
    this.tasks = tasks;
    this.reports = reports;
    this.agentTasks = agentTasks;
  }

  @Transactional(readOnly = true)
  public Page<Sample> records(Long userId, StatisticsQuery q) {
    return new Page<>(mapper.records(userId, q, (q.page - 1L) * q.pageSize),
        mapper.totals(userId, q).recordCount(), q.page, q.pageSize);
  }

  @Transactional(readOnly = true)
  public Statistics statistics(Long userId, LocalDate start, LocalDate end, String source) {
    var q = new StatisticsQuery();
    q.startDate = start;
    q.endDate = end;
    q.sourceName = source;
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
    Identity.requireExisting(tasks.lockUser(userId));
    Sample sample = sample(userId, measurement);
    if (id == null) {
      mapper.insert(sample);
    } else {
      Sample previous = mapper.record(userId, id);
      if (previous == null) throw ApiException.missing();
      sample.id = id;
      sample.sourceName = previous.sourceName;
      mapper.update(sample);
      LocalDate previousDate = previous.measuredAt.toLocalDate();
      agentTasks.invalidateCombined(userId, previousDate, previousDate);
      LocalDate newDate = sample.measuredAt.toLocalDate();
      reports.invalidateHeartRateReports(userId, previousDate);
      if (!newDate.equals(previousDate)) {
        agentTasks.invalidateCombined(userId, newDate, newDate);
        reports.invalidateHeartRateReports(userId, newDate);
      }
    }
    return mapper.record(userId, sample.id);
  }

  @Transactional
  public void delete(Long userId, Long id) {
    Identity.requireExisting(tasks.lockUser(userId));
    Sample sample = mapper.record(userId, id);
    if (sample == null) throw ApiException.missing();
    mapper.delete(userId, id);
    agentTasks.invalidateCombined(userId, sample.measuredAt.toLocalDate(), sample.measuredAt.toLocalDate());
    reports.invalidateHeartRateReports(userId, sample.measuredAt.toLocalDate());
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
}
