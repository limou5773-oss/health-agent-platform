package com.health.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class BaselineModels {
  private BaselineModels() {}

  public enum Metric {
    SLEEP, STEPS, WORKOUT, MEAL, WATER, WEIGHT, HEART_RATE
  }

  public record Period(
      LocalDate startDate, LocalDate endDate, int periodDays, int validDays, int requiredDays,
      BigDecimal coverageRate, boolean sufficient, BigDecimal average, List<LocalDate> missingDates,
      Long recordCount) {}

  public record Comparison(
      Metric metric, String sourceName, Period recent, Period historical, BigDecimal difference) {}
}
