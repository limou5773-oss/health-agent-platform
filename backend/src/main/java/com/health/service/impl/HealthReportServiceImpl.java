package com.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.ApiException;
import com.health.api.HealthReportRequests.Generate;
import com.health.mapper.PlanMapper;
import com.health.mapper.TaskMapper;
import com.health.model.HealthReportModels.*;
import com.health.model.Models.Report;
import com.health.service.AccountService;
import com.health.service.ActivityService;
import com.health.service.HealthReportService;
import com.health.service.NutritionService;
import com.health.service.NotificationService;
import com.health.service.PlanService;
import com.health.service.SleepService;
import com.health.service.WeightService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthReportServiceImpl implements HealthReportService {
  private final AccountService accounts;
  private final SleepService sleep;
  private final ActivityService activity;
  private final NutritionService nutrition;
  private final WeightService weight;
  private final PlanService plans;
  private final PlanMapper mapper;
  private final TaskMapper tasks;
  private final ObjectMapper json;
  private final NotificationService notifications;

  public HealthReportServiceImpl(AccountService accounts, SleepService sleep, ActivityService activity,
      NutritionService nutrition, WeightService weight, PlanService plans, PlanMapper mapper,
      TaskMapper tasks, ObjectMapper json, NotificationService notifications) {
    this.accounts = accounts;
    this.sleep = sleep;
    this.activity = activity;
    this.nutrition = nutrition;
    this.weight = weight;
    this.plans = plans;
    this.mapper = mapper;
    this.tasks = tasks;
    this.json = json;
    this.notifications = notifications;
  }

  @Override
  @Transactional
  public Report generate(Long userId, Generate request) {
    // Record edits use this same user lock before invalidating saved reports.
    tasks.lockUser(userId);
    boolean weekly = request.period() == Period.WEEK;
    LocalDate start = weekly ? request.date().minusDays(6) : YearMonth.from(request.date()).atDay(1);
    LocalDate end = weekly ? request.date() : YearMonth.from(request.date()).atEndOfMonth();
    LocalDate today = LocalDate.now(ZoneId.of(accounts.profile(userId).timezone));
    if (!end.isBefore(today))
      throw new ApiException(400, "INVALID_INPUT", "请选择已结束的报告周期");

    var sleepStats = sleep.statistics(userId, start, end, request.sleepSource());
    var steps = activity.stepStatistics(userId, start, end, request.stepSource());
    var workouts = activity.workoutStatistics(userId, start, end, request.workoutSource());
    var meals = nutrition.mealStatistics(userId, start, end, request.mealSource());
    var water = nutrition.waterStatistics(userId, start, end, request.waterSource());
    var weights = weight.statistics(userId, start, end, request.weightSource());
    Completion completion = mapper.reportCompletion(userId, start, end);
    Map<String, List<String>> sources = mapper.reportSources(userId, start, end, request).stream()
        .collect(Collectors.groupingBy(Source::metric, Collectors.mapping(Source::sourceName, Collectors.toList())));
    List<Metric> metrics = List.of(
        metric("SLEEP", "按醒来日期归属，合并重叠时段后计算有效日均值",
            sleepStats.validDays(), sleepStats.averageHours(), sources),
        metric("STEPS", "每天取最新累计快照或不重叠增量的总和，再计算有效日均值",
            steps.validDays(), steps.averageSteps(), sources),
        metric("WORKOUT", "按周期截取运动时段并合并重叠区间，累计时长", workouts.validDays(),
            workouts.validDays() == 0 ? null : workouts.totalMinutes(), sources),
        new Metric("MEAL", "合计已知的手动和估算热量，覆盖按有饮食记录的天数计算",
            meals.validDays(), meals.knownCaloriesKcal(),
            meals.manualCaloriesCount(), meals.estimatedCaloriesCount(), meals.unknownCaloriesCount(),
            sources.getOrDefault("MEAL", List.of())),
        metric("WATER", "汇总每日饮水量，再计算有效日均值", water.validDays(), water.averageMl(), sources),
        metric("WEIGHT", "取每日最后一次测量，再计算有效日均值", weights.validDays(), weights.averageKg(), sources));
    if (completion.scheduled() == 0 && metrics.stream().noneMatch(m -> m.validDays() > 0))
      throw new ApiException(422, "REPORT_NO_DATA", "所选周期没有健康记录或应完成的计划任务");
    BigDecimal rate = completion.scheduled() == 0 ? null
        : BigDecimal.valueOf(completion.completed() * 100)
            .divide(BigDecimal.valueOf(completion.scheduled()), 2, RoundingMode.HALF_UP);
    Snapshot snapshot = new Snapshot(ChronoUnit.DAYS.between(start, end) + 1, metrics, completion, rate);

    Report report = new Report();
    report.userId = userId;
    report.startDate = start;
    report.endDate = end;
    report.reportType = weekly ? "HEALTH_WEEK" : "HEALTH_MONTH";
    report.title = weekly ? "综合健康周报" : "综合健康月报";
    report.reportVersion = mapper.nextReportVersion(userId, report.reportType, start, end);
    report.statisticsJson = json.valueToTree(snapshot).toString();
    mapper.insertReport(report);
    notifications.reportReady(userId, report.id);
    return plans.report(userId, report.id);
  }

  private static Metric metric(
      String key, String basis, long days, Number value, Map<String, List<String>> sources) {
    return new Metric(key, basis, days, value, 0, 0, 0, sources.getOrDefault(key, List.of()));
  }
}
