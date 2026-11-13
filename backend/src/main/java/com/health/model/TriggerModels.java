package com.health.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TriggerModels {
  private TriggerModels() {}

  public enum Kind { WEEKLY_REVIEW, SLEEP_IMPORT }

  public record Settings(boolean weeklyEnabled, boolean importEnabled,
      int importDays, String sourceName, boolean notifyInApp) {}

  public record Run(Kind kind, String eventKey, LocalDate startDate, LocalDate endDate,
      String sourceName, LocalDateTime createdAt, String taskId, String taskStatus) {}
}
