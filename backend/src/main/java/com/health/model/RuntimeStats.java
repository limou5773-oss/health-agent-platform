package com.health.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RuntimeStats(LocalDate startDate, LocalDate endDate, List<StatusCount> taskStatuses,
    long timedTasks, BigDecimal averageDurationMs, BigDecimal completionRate, List<ToolFailure> toolFailures,
    List<ImportVolume> imports, long scheduledItems, long completedItems, BigDecimal planCompletionRate,
    long firstStepTasks, BigDecimal averageFirstStepMs) {

  public record StatusCount(String status, long count) {}
  public enum TaskStatus { COMPLETED, FAILED, CANCELLED, QUEUED, RUNNING, WAITING_INPUT, WAITING_CONFIRMATION }
  public record TaskRow(String id, String kind, LocalDateTime createdAt, Long durationMs, Long firstStepMs) {}
  public record Timing(long timedTasks, BigDecimal averageDurationMs, long firstStepTasks, BigDecimal averageFirstStepMs) {}
  public record ToolFailure(String tool, long count) {}
  public record ImportVolume(String kind, long batches, long confirmed, long rejected,
      long inputRows, long insertedRows) {}
  public record Completion(long scheduled, long completed) {}
}
