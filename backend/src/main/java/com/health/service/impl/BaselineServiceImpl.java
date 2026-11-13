package com.health.service.impl;

import com.health.api.BaselineRequests.Query;
import com.health.model.BaselineModels.Comparison;
import com.health.model.BaselineModels.Metric;
import com.health.model.BaselineModels.Period;
import com.health.service.AccountService;
import com.health.service.ActivityService;
import com.health.service.BaselineService;
import com.health.service.NutritionService;
import com.health.service.SleepService;
import com.health.service.WeightService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaselineServiceImpl implements BaselineService {
  private final AccountService accounts;
  private final SleepService sleep;
  private final ActivityService activity;
  private final NutritionService nutrition;
  private final WeightService weight;

  public BaselineServiceImpl(
      AccountService accounts, SleepService sleep, ActivityService activity,
      NutritionService nutrition, WeightService weight) {
    this.accounts = accounts;
    this.sleep = sleep;
    this.activity = activity;
    this.nutrition = nutrition;
    this.weight = weight;
  }

  @Override
  @Transactional(readOnly = true)
  public Comparison compare(Long userId, Query query) {
    boolean preset = query.recentStart() == null;
    LocalDate recentEnd = preset ? LocalDate.now(ZoneId.of(accounts.profile(userId).timezone)) : query.recentEnd();
    LocalDate recentStart = preset ? recentEnd.minusDays(6) : query.recentStart();
    LocalDate historicalEnd = preset ? recentStart.minusDays(1) : query.historicalEnd();
    LocalDate historicalStart = preset ? historicalEnd.minusDays(27) : query.historicalStart();
    String source = query.sourceName() == null || query.sourceName().isBlank() ? null : query.sourceName();
    Period recent = period(userId, query.metric(), source, recentStart, recentEnd, preset ? 5 : 0);
    Period historical = period(userId, query.metric(), source, historicalStart, historicalEnd, preset ? 20 : 0);
    BigDecimal difference = recent.sufficient() && historical.sufficient()
        ? recent.average().subtract(historical.average()) : null;
    return new Comparison(query.metric(), source, recent, historical, difference);
  }

  private Period period(
      Long userId, Metric metric, String source, LocalDate start, LocalDate end, int presetRequiredDays) {
    // Each statistics service returns one ordered entry per calendar date, with null for missing values.
    List<? extends Number> values = switch (metric) {
      case SLEEP -> sleep.statistics(userId, start, end, source).daily().stream()
          .map(day -> day.hours()).toList();
      case STEPS -> activity.stepStatistics(userId, start, end, source).daily().stream()
          .map(day -> day.steps()).toList();
      case WORKOUT -> activity.workoutStatistics(userId, start, end, source).daily().stream()
          .map(day -> day.minutes()).toList();
      case MEAL -> nutrition.mealStatistics(userId, start, end, source).daily().stream()
          .map(day -> day.unknownCaloriesCount() == 0 ? day.knownCaloriesKcal() : null).toList();
      case WATER -> nutrition.waterStatistics(userId, start, end, source).daily().stream()
          .map(day -> day.amountMl()).toList();
      case WEIGHT -> weight.statistics(userId, start, end, source).daily().stream()
          .map(day -> day.representative() == null ? null : day.representative().weightKg).toList();
    };
    List<LocalDate> missing = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    for (int i = 0; i < values.size(); i++) {
      Number value = values.get(i);
      if (value == null) {
        missing.add(start.plusDays(i));
      } else {
        total = total.add(new BigDecimal(value.toString()));
      }
    }
    int days = values.size();
    int valid = days - missing.size();
    int required = presetRequiredDays > 0 ? presetRequiredDays : (days * 7 + 9) / 10;
    BigDecimal average = valid == 0 ? null : total.divide(BigDecimal.valueOf(valid), 3, RoundingMode.HALF_UP);
    BigDecimal coverage = BigDecimal.valueOf(valid * 100L)
        .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    return new Period(start, end, days, valid, required, coverage, valid >= required, average, missing);
  }
}
