package com.health.service.impl;

import com.health.api.ApiException;
import com.health.api.HealthReportRequests.Generate;
import com.health.mapper.PlanMapper;
import com.health.model.CombinedAnalysis;
import com.health.model.CombinedAnalysis.Day;
import com.health.model.CombinedAnalysis.Metric;
import com.health.model.HealthReportModels.Source;
import com.health.model.Models.Task;
import com.health.model.Models.Profile;
import com.health.service.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CombinedAnalysisServiceImpl implements CombinedAnalysisService {
  private final PlanMapper reports;
  private final SleepService sleep;
  private final ActivityService activity;
  private final WeightService weight;
  private final HeartRateService heartRate;

  public CombinedAnalysisServiceImpl(PlanMapper reports, SleepService sleep, ActivityService activity,
      WeightService weight, HeartRateService heartRate) {
    this.reports = reports;
    this.sleep = sleep;
    this.activity = activity;
    this.weight = weight;
    this.heartRate = heartRate;
  }

  @Transactional(readOnly = true)
  public CombinedAnalysis statistics(Task task, Profile profile) {
    var start = task.startDate;
    var end = task.endDate;
    Long userId = task.userId;
    var filters = new Generate(null, null, task.sourceName, null, null, null, null, null, null);
    var sources = reports.reportSources(userId, start, end, filters).stream()
        .collect(Collectors.groupingBy(Source::metric, Collectors.mapping(Source::sourceName, Collectors.toList())));
    var metrics = java.util.stream.Stream.of(
        profile.aiSleep ? metric("SLEEP", "小时", "按醒来日期汇总睡眠时长", sources.getOrDefault("SLEEP", List.of()), () -> {
          var stats = sleep.statistics(userId, start, end, task.sourceName);
          return new Values(stats.validDays(), (long) stats.recordCount(),
              stats.daily().stream().map(d -> new Day(d.date(), d.hours())).toList());
        }) : null,
        profile.aiActivity ? metric("STEPS", "步", "每日取最新累计快照或区间增量合计", sources.getOrDefault("STEPS", List.of()), () -> {
          var stats = activity.stepStatistics(userId, start, end, null);
          return new Values(stats.validDays(), null,
              stats.daily().stream().map(d -> new Day(d.date(), d.steps())).toList());
        }) : null,
        profile.aiActivity ? metric("WORKOUT", "分钟", "按日合并实际运动时段", sources.getOrDefault("WORKOUT", List.of()), () -> {
          var stats = activity.workoutStatistics(userId, start, end, null);
          return new Values(stats.validDays(), (long) stats.recordCount(),
              stats.daily().stream().map(d -> new Day(d.date(), d.minutes())).toList());
        }) : null,
        profile.aiWeight ? metric("WEIGHT", "千克", "每日取最后一次测量", sources.getOrDefault("WEIGHT", List.of()), () -> {
          var stats = weight.statistics(userId, start, end, null);
          return new Values(stats.validDays(), (long) stats.recordCount(), stats.daily().stream()
              .map(d -> new Day(d.date(), d.representative() == null ? null : d.representative().weightKg)).toList());
        }) : null,
        profile.aiHeartRate ? metric("HEART_RATE", "次/分", "逐日计算实际采样均值", sources.getOrDefault("HEART_RATE", List.of()), () -> {
          var stats = heartRate.statistics(userId, start, end, null);
          return new Values(stats.validDays(), stats.recordCount(),
              stats.daily().stream().map(d -> new Day(d.date(), d.averageBpm())).toList());
        }) : null
    ).filter(Objects::nonNull).toList();
    return new CombinedAnalysis(start, end, ChronoUnit.DAYS.between(start, end) + 1, metrics);
  }

  private record Values(long validDays, Long recordCount, List<Day> daily) {}

  private static Metric metric(String key, String unit, String basis, List<String> sources, Supplier<Values> read) {
    try {
      var values = read.get();
      return new Metric(key, unit, basis, values.validDays(), values.recordCount(), sources, values.daily(), null);
    } catch (ApiException e) {
      if (!List.of("SOURCE_SELECTION_REQUIRED", "SOURCE_CONFLICT").contains(e.code)) throw e;
      return new Metric(key, unit, basis, null, null, sources, List.of(), e.getMessage());
    }
  }
}
