package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.*;
import java.util.List;

public final class ActivityModels {
  private ActivityModels() {}

  public static class StepRecord {
    public Long id;
    @JsonIgnore public Long userId;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public LocalDate activityDate;
    public int steps;
    public String mode;
    public String sourceName;
    public String note;
    public boolean corrected;
    public LocalDateTime createdAt;
  }

  public static class Workout {
    public Long id;
    @JsonIgnore public Long userId;
    public String exerciseType;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public LocalDate activityDate;
    public double durationMinutes;
    public String sourceName;
    public String note;
    public Long planItemId;
    public Long planId;
    public String planItemTitle;
    public Boolean planItemCompleted;
    public LocalDateTime createdAt;
  }

  public enum StepImportFormat { CSV, APPLE_HEALTH }

  public static class StepImportBatch {
    public Long id;
    @JsonIgnore public Long userId;
    public String status;
    public StepImportFormat importFormat;
    public int totalRows;
    public int skippedRows;
    public int validRows;
    public int invalidRows;
    public int duplicateRows;
    public int insertedRows;
    public LocalDateTime createdAt;
    public LocalDateTime firstStartTime;
    public LocalDateTime lastEndTime;
    public List<StepImportRow> rows;
  }

  public static class StepImportRow {
    @JsonIgnore public Long batchId;
    public int rowNumber;
    public String startTime;
    public String endTime;
    public String steps;
    public String mode;
    public String sourceName;
    public String note;
    public boolean valid;
    public String error;
  }

  public static class EligiblePlanItem {
    public Long id;
    public Long planId;
    public String title;
    public String planTitle;
    public LocalDate date;
    public boolean completed;
  }

  public record Goal(Integer dailySteps) {}
  public record StepDaily(LocalDate date, Long steps, String sourceName, Double goalRate) {}
  public record StepStatistics(
      LocalDate startDate, LocalDate endDate, String sourceName, long periodDays,
      long validDays, double coverageRate, long totalSteps, Double averageSteps,
      Integer dailyGoal, Long goalDays, LocalDateTime dataCutoff, List<StepDaily> daily) {}
  public record WorkoutDaily(LocalDate date, Double minutes, int recordCount) {}
  public record WorkoutStatistics(
      LocalDate startDate, LocalDate endDate, String sourceName, long periodDays,
      long validDays, double coverageRate, double totalMinutes, int recordCount,
      List<WorkoutDaily> daily) {}
}
