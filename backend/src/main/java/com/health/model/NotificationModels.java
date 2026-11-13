package com.health.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

public final class NotificationModels {
  private NotificationModels() {}

  public enum Kind { CHECKIN, PLAN_DUE, REPORT, DATA_CHANGE, IMPORT_FAILED, AUTO_TASK }
  public enum CheckinFrequency { PER_TASK, DAILY }

  public record Settings(boolean checkinEnabled, CheckinFrequency checkinFrequency,
      LocalTime dailyTime, boolean planDueEnabled, boolean reportEnabled,
      boolean dataChangeEnabled, boolean importFailedEnabled) {}

  public record Message(Long id, Kind kind, String title, String body, String target,
      LocalDateTime dataCutoff, LocalDateTime createdAt, LocalDateTime readAt) {}
}
