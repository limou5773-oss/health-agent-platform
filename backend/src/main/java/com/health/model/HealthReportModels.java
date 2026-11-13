package com.health.model;

import java.math.BigDecimal;
import java.util.List;

public final class HealthReportModels {
  private HealthReportModels() {}

  public enum Period { WEEK, MONTH }

  public record Source(String metric, String sourceName) {}
  public record Metric(
      String key, String basis, long validDays, Number value,
      int manualCaloriesCount, int estimatedCaloriesCount, int unknownCaloriesCount,
      List<String> sources) {}
  public record Completion(long scheduled, long completed) {}
  public record Snapshot(long periodDays, List<Metric> metrics, Completion completion,
                         BigDecimal completionRate) {}

  public record FocusedPeriod(BaselineModels.Period statistics, List<String> sources,
      Completion completion, BigDecimal completionRate) {}

  public record FocusedSnapshot(BaselineModels.Metric metric, String sourceName, FocusedPeriod current,
      FocusedPeriod comparison, BigDecimal difference) {}
}
