package com.health.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.health.model.BaselineModels.Metric;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class HealthGoalRequests {
  private HealthGoalRequests() {}

  public enum Direction { AT_LEAST, AT_MOST }

  public record Save(
      @NotBlank @Size(max = 200) String title,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @NotNull Metric metric,
      @NotNull Direction direction,
      @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal targetValue,
      @NotNull @Size(max = 500) String preferences) {
    @AssertTrue(message = "目标周期须为1至366天")
    @JsonIgnore
    public boolean isDateRangeValid() {
      if (startDate == null || endDate == null) return true;
      long days = ChronoUnit.DAYS.between(startDate, endDate);
      return days >= 0 && days < 366;
    }
  }

  public record Link(@JsonProperty(required = true) @Positive Long goalId) {}
}
