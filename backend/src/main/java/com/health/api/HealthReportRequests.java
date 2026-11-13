package com.health.api;

import com.health.model.HealthReportModels.Period;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class HealthReportRequests {
  private HealthReportRequests() {}

  public record Generate(
      @NotNull Period period, @NotNull LocalDate date,
      @Size(max = 100) String sleepSource, @Size(max = 100) String stepSource,
      @Size(max = 100) String workoutSource, @Size(max = 100) String mealSource,
      @Size(max = 100) String waterSource, @Size(max = 100) String weightSource) {
    public Generate {
      sleepSource = selected(sleepSource);
      stepSource = selected(stepSource);
      workoutSource = selected(workoutSource);
      mealSource = selected(mealSource);
      waterSource = selected(waterSource);
      weightSource = selected(weightSource);
    }

    private static String selected(String source) {
      return source == null || source.isBlank() ? null : source;
    }
  }
}
