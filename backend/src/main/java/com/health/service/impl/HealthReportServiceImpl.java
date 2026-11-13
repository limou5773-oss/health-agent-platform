package com.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.ApiException;
import com.health.api.HealthReportRequests.Generate;
import com.health.api.HealthReportRequests.Focused;
import com.health.mapper.PlanMapper;
import com.health.mapper.TaskMapper;
import com.health.model.BaselineModels;
import com.health.model.HealthReportModels.*;
import com.health.model.Models.Report;
import com.health.service.AccountService;
import com.health.service.ActivityService;
import com.health.service.BaselineService;
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
  private final BaselineService baseline;

  public HealthReportServiceImpl(AccountService accounts, SleepService sleep, ActivityService activity,
      NutritionService nutrition, WeightService weight, PlanService plans, PlanMapper mapper,
      TaskMapper tasks, ObjectMapper json, NotificationService notifications, BaselineService baseline) {
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
    this.baseline = baseline;
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
    Snapshot snapshot = new Snapshot(ChronoUnit.DAYS.between(start, end) + 1, metrics, completion, rate(completion));

    Report report = new Report();
    report.userId = userId;
    report.startDate = start;
    report.endDate = end;
    report.reportType = weekly ? "HEALTH_WEEK" : "HEALTH_MONTH";
    report.title = weekly ? "综合健康周报" : "综合健康月报";
    report.reportVersion = mapper.nextReportVersion(userId, report.reportType, start, end);
    report.statisticsJson = json.valueToTree(snapshot).toString();
    return save(report);
  }

  @Override
  @Transactional
  public Report focused(Long userId, Focused request) {
    tasks.lockUser(userId);
    LocalDate today = LocalDate.now(ZoneId.of(accounts.profile(userId).timezone));
    if (!request.endDate().isBefore(today))
      throw new ApiException(400, "INVALID_INPUT", "请选择已结束的报告周期");
    String source = request.sourceName() == null || request.sourceName().isBlank() ? null : request.sourceName();
    var metric = request.metric();
    // Reuse the same source predicates as the comprehensive report and the metric statistics.
    Generate filter = new Generate(null, null,
        metric == BaselineModels.Metric.SLEEP ? source : null,
        metric == BaselineModels.Metric.STEPS ? source : null,
        metric == BaselineModels.Metric.WORKOUT ? source : null,
        metric == BaselineModels.Metric.MEAL ? source : null,
        metric == BaselineModels.Metric.WATER ? source : null,
        metric == BaselineModels.Metric.WEIGHT ? source : null);
    var current = focusedPeriod(userId, metric, source, request.startDate(), request.endDate(), filter);
    var comparison = request.comparisonStartDate() == null ? null
        : focusedPeriod(userId, metric, source, request.comparisonStartDate(), request.comparisonEndDate(), filter);
    if (current.statistics().validDays() == 0 && (comparison == null || comparison.statistics().validDays() == 0))
      throw new ApiException(422, "REPORT_NO_DATA", "所选周期没有该指标的有效记录");
    BigDecimal difference = comparison != null && current.statistics().sufficient() && comparison.statistics().sufficient()
        ? current.statistics().average().subtract(comparison.statistics().average()) : null;
    String label = switch (metric) {
      case SLEEP -> "睡眠时长";
      case STEPS -> "步数";
      case WORKOUT -> "运动时长";
      case MEAL -> "饮食热量";
      case WATER -> "饮水量";
      case WEIGHT -> "体重";
    };
    Report report = new Report();
    report.userId = userId;
    report.startDate = request.startDate();
    report.endDate = request.endDate();
    report.comparisonStartDate = request.comparisonStartDate();
    report.comparisonEndDate = request.comparisonEndDate();
    report.metric = metric.name();
    report.sourceName = source;
    report.reportType = comparison == null ? "HEALTH_FOCUSED" : "HEALTH_COMPARISON";
    report.title = label + (comparison == null ? "专项报告" : "阶段对比");
    report.reportVersion = mapper.nextFocusedVersion(report);
    report.statisticsJson = json.valueToTree(new FocusedSnapshot(metric, source, current, comparison, difference)).toString();
    return save(report);
  }

  private FocusedPeriod focusedPeriod(Long userId, BaselineModels.Metric metric,
      String source, LocalDate start, LocalDate end, Generate filter) {
    var statistics = baseline.summarize(userId, metric, source, start, end);
    var sources = mapper.reportSources(userId, start, end, filter).stream()
        .filter(row -> row.metric().equals(metric.name())).map(Source::sourceName).toList();
    var completion = mapper.reportCompletion(userId, start, end);
    return new FocusedPeriod(statistics, sources, completion, rate(completion));
  }

  private Report save(Report report) {
    mapper.insertReport(report);
    notifications.reportReady(report.userId, report.id);
    return plans.report(report.userId, report.id);
  }

  private static BigDecimal rate(Completion completion) {
    return completion.scheduled() == 0 ? null : BigDecimal.valueOf(completion.completed() * 100)
        .divide(BigDecimal.valueOf(completion.scheduled()), 2, RoundingMode.HALF_UP);
  }

  private static Metric metric(
      String key, String basis, long days, Number value, Map<String, List<String>> sources) {
    return new Metric(key, basis, days, value, 0, 0, 0, sources.getOrDefault(key, List.of()));
  }
}
