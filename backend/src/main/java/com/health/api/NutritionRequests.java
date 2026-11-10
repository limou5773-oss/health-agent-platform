package com.health.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class NutritionRequests {
  private NutritionRequests() {}

  public record Meal(
      @NotNull LocalDateTime consumedAt,
      @NotNull @Pattern(regexp = "BREAKFAST|LUNCH|DINNER|SNACK") String mealType,
      @NotBlank @Size(max = 100) String foodName,
      @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal portionAmount,
      @NotNull @Pattern(regexp = "G|ML|SERVING") String portionUnit,
      @NotNull @Pattern(regexp = "UNKNOWN|MANUAL|ESTIMATED") String calorieMode,
      @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal caloriesKcal,
      @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal referenceAmount,
      @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal referenceCaloriesKcal,
      @NotNull @Size(max = 500) String nutritionSource,
      @NotBlank @Size(max = 100) String sourceName,
      @NotNull @Size(max = 1000) String note) {
    public Meal {
      if (consumedAt != null) consumedAt = consumedAt.truncatedTo(ChronoUnit.SECONDS);
    }

    @AssertTrue(message = "未知热量须留空；手动热量须填写且不含参考值；估算须提供份量、参考份量、参考热量和营养依据，热量由平台计算")
    @JsonIgnore
    public boolean isCalorieBasisValid() {
      if (calorieMode == null) return true;
      return switch (calorieMode) {
        case "UNKNOWN" -> caloriesKcal == null && referenceAmount == null && referenceCaloriesKcal == null;
        case "MANUAL" -> caloriesKcal != null && referenceAmount == null && referenceCaloriesKcal == null;
        case "ESTIMATED" -> caloriesKcal == null && portionAmount != null && referenceAmount != null
            && referenceCaloriesKcal != null && nutritionSource != null && !nutritionSource.isBlank();
        default -> true; // The field constraint reports an unsupported mode.
      };
    }
  }

  public record Water(
      @NotNull LocalDateTime consumedAt,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 2) BigDecimal amountMl,
      @NotBlank @Size(max = 100) String sourceName,
      @NotNull @Size(max = 1000) String note) {
    public Water {
      if (consumedAt != null) consumedAt = consumedAt.truncatedTo(ChronoUnit.SECONDS);
    }
  }
}
