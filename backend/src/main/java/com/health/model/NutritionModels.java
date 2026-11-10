package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class NutritionModels {
  private NutritionModels() {}

  public static class MealRecord {
    public Long id;
    @JsonIgnore public Long userId;
    public LocalDateTime consumedAt;
    public String mealType;
    public String foodName;
    public BigDecimal portionAmount;
    public String portionUnit;
    public String calorieMode;
    public BigDecimal caloriesKcal;
    public BigDecimal referenceAmount;
    public BigDecimal referenceCaloriesKcal;
    public String nutritionSource;
    public String sourceName;
    public String note;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
  }

  public static class WaterRecord {
    public Long id;
    @JsonIgnore public Long userId;
    public LocalDateTime consumedAt;
    public BigDecimal amountMl;
    public String sourceName;
    public String note;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
  }

  public record MealDaily(
      LocalDate date, int recordCount, int knownCaloriesCount, int unknownCaloriesCount,
      int manualCaloriesCount, int estimatedCaloriesCount,
      BigDecimal manualCaloriesKcal, BigDecimal estimatedCaloriesKcal, BigDecimal knownCaloriesKcal) {}

  public record MealStatistics(
      LocalDate startDate, LocalDate endDate, String sourceName, long periodDays,
      long validDays, BigDecimal coverageRate, int recordCount, int knownCaloriesCount,
      int unknownCaloriesCount, int manualCaloriesCount, int estimatedCaloriesCount,
      BigDecimal manualCaloriesKcal, BigDecimal estimatedCaloriesKcal,
      BigDecimal knownCaloriesKcal, LocalDateTime dataCutoff, List<MealDaily> daily) {}

  public record WaterDaily(LocalDate date, BigDecimal amountMl, int recordCount) {}

  public record WaterStatistics(
      LocalDate startDate, LocalDate endDate, String sourceName, long periodDays,
      long validDays, BigDecimal coverageRate, int recordCount, BigDecimal totalMl,
      BigDecimal averageMl, LocalDateTime dataCutoff, List<WaterDaily> daily) {}
}
