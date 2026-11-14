package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class WeightModels {
  private WeightModels() {}

  public static class WeightRecord {
    public Long id;
    @JsonIgnore public Long userId;
    public LocalDateTime measuredAt;
    public BigDecimal weightKg;
    public String sourceName;
    public String note;
    public boolean corrected;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
  }

  public static class WeightGoal {
    @JsonIgnore public Long userId;
    public LocalDate startDate;
    public LocalDate targetDate;
    public BigDecimal targetWeightKg;
    public String sourceName;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
  }

  public record WeightDaily(LocalDate date, int recordCount, WeightRecord representative) {}

  public record WeightStatistics(
      LocalDate startDate, LocalDate endDate, String sourceName, long periodDays,
      long validDays, BigDecimal coverageRate, int recordCount, BigDecimal averageKg,
      WeightRecord first, WeightRecord last, BigDecimal changeKg, LocalDateTime dataCutoff,
      List<WeightDaily> daily) {}

  public record WeightGoalProgress(
      WeightGoal goal, long periodDays, long validDays, BigDecimal coverageRate,
      WeightRecord baseline, WeightRecord current, BigDecimal changeKg,
      BigDecimal differenceToTargetKg, BigDecimal progressRate, String direction,
      Boolean goalReached, LocalDateTime dataCutoff) {}
}
