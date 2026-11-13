package com.health.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RuntimeStats(LocalDate startDate, LocalDate endDate, List<StatusCount> taskStatuses,
    long timedTasks, BigDecimal averageDurationMs, BigDecimal completionRate, List<ToolFailure> toolFailures,
    List<ImportVolume> imports, long scheduledItems, long completedItems, BigDecimal planCompletionRate) {

  public record StatusCount(String status, long count) {}
  public record Timing(long timedTasks, BigDecimal averageDurationMs) {}
  public record ToolFailure(String tool, long count) {}
  public record ImportVolume(String kind, long batches, long confirmed, long rejected,
      long inputRows, long insertedRows) {}
  public record Completion(long scheduled, long completed) {}
}
