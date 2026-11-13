package com.health.api;

import com.health.model.HealthReportModels.Period;
import com.health.model.BaselineModels.Metric;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class HealthReportRequests {
  private HealthReportRequests() {}

  public record Focused(@NotNull Metric metric, @Size(max = 100) String sourceName,
      @NotNull LocalDate startDate, @NotNull LocalDate endDate,
      LocalDate comparisonStartDate, LocalDate comparisonEndDate) {
    @AssertTrue(message = "每期须为1至366天，对照周期须完整且早于报告周期")
    public boolean isRangeValid() {
      if (startDate == null || endDate == null) return true;
      return valid(startDate, endDate) &&
          (comparisonStartDate == null && comparisonEndDate == null ||
           comparisonStartDate != null && comparisonEndDate != null &&
           valid(comparisonStartDate, comparisonEndDate) && comparisonEndDate.isBefore(startDate));
    }

    private static boolean valid(LocalDate start, LocalDate end) {
      long days = ChronoUnit.DAYS.between(start, end);
      return days >= 0 && days < 366;
    }
  }

  public record Generate(
      @NotNull Period period, @NotNull LocalDate date,
      @Size(max = 100) String sleepSource, @Size(max = 100) String stepSource,
      @Size(max = 100) String workoutSource, @Size(max = 100) String mealSource,
      @Size(max = 100) String waterSource, @Size(max = 100) String weightSource,
      @Size(max = 100) String heartRateSource) {
    public Generate {
      sleepSource = selected(sleepSource);
      stepSource = selected(stepSource);
      workoutSource = selected(workoutSource);
      mealSource = selected(mealSource);
      waterSource = selected(waterSource);
      weightSource = selected(weightSource);
      heartRateSource = selected(heartRateSource);
    }

    private static String selected(String source) {
      return source == null || source.isBlank() ? null : source;
    }
  }
}
