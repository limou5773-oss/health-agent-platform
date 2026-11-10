package com.health.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class WeightRequests {
  private WeightRequests() {}

  public record Measurement(
      @NotNull LocalDateTime measuredAt,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 3) BigDecimal weightKg,
      @NotBlank @Size(max = 100) String sourceName,
      @NotNull @Size(max = 1000) String note) {
    public Measurement {
      if (measuredAt != null) measuredAt = measuredAt.truncatedTo(ChronoUnit.SECONDS);
    }
  }

  public record Goal(
      @NotNull LocalDate startDate,
      @NotNull LocalDate targetDate,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 3) BigDecimal targetWeightKg,
      @Size(max = 100) String sourceName) {
    @AssertTrue(message = "目标周期须为1至366天")
    @JsonIgnore
    public boolean isRangeValid() {
      if (startDate == null || targetDate == null) return true;
      long days = ChronoUnit.DAYS.between(startDate, targetDate) + 1;
      return days >= 1 && days <= 366;
    }
  }
}
