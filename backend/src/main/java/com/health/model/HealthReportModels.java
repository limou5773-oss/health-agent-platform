package com.health.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class HealthReportModels {
  private HealthReportModels() {}

  public enum Period { WEEK, MONTH }

  public record Source(String metric, String sourceName) {}
  public record Metric(
      String key, String basis, long validDays, Number value,
      int manualCaloriesCount, int estimatedCaloriesCount, int unknownCaloriesCount,
      List<String> sources, Long recordCount) {}
  public record Completion(long scheduled, long completed) {
    public BigDecimal rate() {
      return scheduled == 0 ? null : BigDecimal.valueOf(completed * 100)
          .divide(BigDecimal.valueOf(scheduled), 2, RoundingMode.HALF_UP);
    }
  }
  public record Snapshot(long periodDays, List<Metric> metrics, Completion completion,
                         BigDecimal completionRate) {}

  public record FocusedPeriod(BaselineModels.Period statistics, List<String> sources,
      Completion completion, BigDecimal completionRate) {}

  public record FocusedSnapshot(BaselineModels.Metric metric, String sourceName, FocusedPeriod current,
      FocusedPeriod comparison, BigDecimal difference) {}
}
