package com.health.api;

import com.health.model.BaselineModels.Metric;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;

public final class BaselineRequests {
  private BaselineRequests() {}

  public record Query(
      @NotNull Metric metric,
      @Size(max = 100) String sourceName,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recentStart,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recentEnd,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate historicalStart,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate historicalEnd) {

    @AssertTrue(message = "自选比较需填写两个完整周期，每期为1至366天，历史周期须在近期周期之前")
    public boolean isPeriodValid() {
      if (recentStart == null && recentEnd == null && historicalStart == null && historicalEnd == null)
        return true;
      return validRange(recentStart, recentEnd) && validRange(historicalStart, historicalEnd)
          && historicalEnd.isBefore(recentStart);
    }

    private static boolean validRange(LocalDate start, LocalDate end) {
      if (start == null || end == null) return false;
      long days = ChronoUnit.DAYS.between(start, end) + 1;
      return days >= 1 && days <= 366;
    }
  }
}
