package com.health.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;

public final class ActivityRequests {
  private ActivityRequests() {}

  public record Step(
      @NotNull LocalDateTime startTime,
      @NotNull LocalDateTime endTime,
      @NotNull @DecimalMin("0") @DecimalMax("2147483647") @Digits(integer = 10, fraction = 0) BigDecimal steps,
      @NotNull @Pattern(regexp = "CUMULATIVE|DELTA") String mode,
      @NotBlank @Size(max = 100) String sourceName,
      @NotNull @Size(max = 1000) String note) {
    public Step {
      if (startTime != null) startTime = startTime.truncatedTo(ChronoUnit.SECONDS);
      if (endTime != null) endTime = endTime.truncatedTo(ChronoUnit.SECONDS);
    }

    @AssertTrue(message = "步数区间至少1秒，须在同一本地日期内，可结束于次日零点")
    @JsonIgnore
    public boolean isIntervalValid() {
      return startTime == null || endTime == null
          || (endTime.isAfter(startTime)
              && !endTime.isAfter(startTime.toLocalDate().plusDays(1).atStartOfDay()));
    }

    @AssertTrue(message = "累计步数须从当日零点开始")
    @JsonIgnore
    public boolean isCumulativeStartValid() {
      return startTime == null || !"CUMULATIVE".equals(mode)
          || startTime.toLocalTime().equals(LocalTime.MIDNIGHT);
    }
  }

  public record Workout(
      @NotBlank @Size(max = 50) String exerciseType,
      @NotNull LocalDateTime startTime,
      @NotNull LocalDateTime endTime,
      @NotBlank @Size(max = 100) String sourceName,
      @NotNull @Size(max = 1000) String note,
      @Positive Long planItemId) {
    public Workout {
      if (startTime != null) startTime = startTime.truncatedTo(ChronoUnit.SECONDS);
      if (endTime != null) endTime = endTime.truncatedTo(ChronoUnit.SECONDS);
    }

    @AssertTrue(message = "运动结束须晚于开始且至少1秒")
    @JsonIgnore
    public boolean isIntervalValid() {
      return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
  }

  public record Goal(
      @NotNull @DecimalMin("1") @DecimalMax("2147483647") @Digits(integer = 10, fraction = 0)
      BigDecimal dailySteps) {}
}
