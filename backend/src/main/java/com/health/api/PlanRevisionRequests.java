package com.health.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class PlanRevisionRequests {
  private PlanRevisionRequests() {}

  public record Item(Long id, @NotNull LocalDate date, @NotNull LocalTime time,
      @NotBlank @Size(max = 200) String title, @NotNull @Size(max = 2000) String description,
      LocalTime reminderTime) {}

  public record Content(
      @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 4000) String reason,
      @NotNull LocalDate startDate, @NotNull LocalDate endDate,
      @NotNull @Size(min = 1, max = 93) List<@NotNull @Valid Item> items) {

    @AssertTrue(message = "计划周期须为1至31天，项目日期须在周期内")
    @JsonIgnore
    public boolean isRangeValid() {
      if (startDate == null || endDate == null || items == null) return true;
      long days = ChronoUnit.DAYS.between(startDate, endDate);
      return days >= 0 && days < 31 && items.stream().allMatch(i -> i == null || i.date() == null
          || (!i.date().isBefore(startDate) && !i.date().isAfter(endDate)));
    }
  }

  public record Save(@Min(1) int expectedVersion, @NotBlank @Size(max = 1000) String note,
      @NotNull @Valid Content content) {}
}
