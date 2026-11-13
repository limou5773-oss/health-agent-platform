package com.health.api;

import com.health.model.PlanTemplateModels.Category;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class PlanTemplateRequests {
  private PlanTemplateRequests() {}

  public record Item(@Min(1) @Max(31) int day, @NotNull LocalTime time,
      @NotBlank @Size(max = 200) String title, @NotNull @Size(max = 2000) String description) {}

  public record Save(@Min(0) int version, @NotNull Category category,
      @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 4000) String instructions,
      @Min(1) @Max(31) int days, @NotNull Boolean enabled,
      @NotNull @Size(min = 1, max = 93) List<@NotNull @Valid Item> items) {
    @AssertTrue(message = "模板任务天数不能超出计划周期")
    @JsonIgnore
    public boolean isRangeValid() {
      return items == null || items.stream().allMatch(item -> item == null || item.day() <= days);
    }
  }

  public record Use(@Min(1) int version, @NotNull LocalDate startDate) {}
}
