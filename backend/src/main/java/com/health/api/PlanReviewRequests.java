package com.health.api;

import com.health.model.BaselineModels.Metric;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;

public final class PlanReviewRequests {
  private PlanReviewRequests() {}

  public record Query(
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @NotNull Metric metric, @Size(max = 100) String sourceName) {
    @AssertTrue(message = "复盘周期须为1至31天")
    public boolean isRangeValid() {
      if (startDate == null || endDate == null) return true;
      long days = ChronoUnit.DAYS.between(startDate, endDate);
      return days >= 0 && days < 31;
    }
  }

  public record Reason(@NotNull @Size(max = 500) String reason) {}

  public record OutcomeQuery(@NotNull Metric metric, @Size(max = 100) String sourceName) {}
}
