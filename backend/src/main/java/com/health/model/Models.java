package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class Models {
  private Models() {}

  public static class User {
    public Long id;
    public String username;
    public String nickname;
    public String role;
    public boolean enabled;
    public LocalDateTime createdAt;
    @JsonIgnore public String passwordHash;
  }

  public static class Profile {
    @JsonIgnore public Long userId;
    public String nickname;
    public BigDecimal heightCm;
    public BigDecimal weightKg;
    public String timezone;
    public String goal;
    public String preferences;
    public boolean aiConsent;
  }

  public static class SleepRecord {
    public Long id;
    @JsonIgnore public Long userId;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public LocalDate sleepDate;
    public double durationMinutes;
    public String sourceName;
    public String sourceApp = "OTHER";
    public String deviceBrand = "OTHER";
    public String deviceType = "OTHER";
    public String deviceModel = "";
    public String note;
    public boolean corrected;
    public LocalDateTime createdAt;
    @JsonIgnore public String dedupKey;
  }

  public static class ImportBatch {
    public Long id;
    @JsonIgnore public Long userId;
    public String status;
    public String format;
    public String sourceApp;
    public String deviceBrand;
    public String deviceType;
    public String deviceModel;
    public int totalRows;
    public int validRows;
    public int invalidRows;
    public int skippedRows;
    public int unsupportedRows;
    public int duplicateRows;
    public int insertedRows;
    public LocalDateTime createdAt;
    public List<ImportRow> rows;
  }

  public static class ImportRow {
    @JsonIgnore public Long batchId;
    public int rowNumber;
    public String startTime;
    public String endTime;
    public LocalDate sleepDate;
    public String sourceName;
    public String note;
    public boolean valid;
    public String error;
    @JsonIgnore public String dedupKey;
  }

  public static class Task {
    public String id;
    @JsonIgnore public Long userId;
    public String kind;
    public String goal;
    public String status;
    public LocalDate startDate;
    public LocalDate endDate;
    public LocalDate planStartDate;
    public LocalDate planEndDate;
    public String sourceName;
    public String answer;
    public String question;
    public String error;
    public boolean retryable;
    @JsonIgnore public String statisticsJson;
    @JsonIgnore public String draftJson;
    @JsonIgnore public String taskToken;
    @JsonIgnore public String pendingInput;
    @JsonIgnore public Integer confirmedVersion;
    @JsonIgnore public String operationId;
    public Long planId;
    public Long reportId;
    public LocalDateTime createdAt;
    @JsonIgnore public LocalDateTime updatedAt;
    public JsonNode statistics;
    public JsonNode draft;
    public List<Step> steps;
  }

  public static class Step {
    public Long id;
    @JsonIgnore public String taskId;
    public String name;
    public String status;
    public String inputSummary;
    public String outputSummary;
    public LocalDateTime createdAt;
  }

  public static class Plan {
    public Long id;
    @JsonIgnore public Long userId;
    @JsonIgnore public String taskId;
    public String title;
    public String reason;
    public LocalDate startDate;
    public LocalDate endDate;
    public String status;
    public LocalDateTime createdAt;
    public List<PlanItem> items;
  }

  public static class PlanItem {
    public Long id;
    @JsonIgnore public Long planId;
    public LocalDate date;
    public LocalTime time;
    public String title;
    public String description;
    public boolean completed;
    public LocalDateTime checkedAt;
  }

  public static class Report {
    public Long id;
    @JsonIgnore public Long userId;
    @JsonIgnore public String taskId;
    @JsonIgnore public String sourceName;
    public String title;
    public LocalDate startDate;
    public LocalDate endDate;
    public LocalDateTime createdAt;
    public boolean sourceDeleted;
    public String content;
    @JsonIgnore public String statisticsJson;
    @JsonIgnore public String referencesJson;
    public JsonNode statistics;
    public JsonNode references;
  }

  public static class Article {
    public Long id;
    public String title;
    public String content;
    public String sourceName;
    public String sourceUrl;
    public LocalDateTime updatedAt;
    public boolean published;
  }

  public record Daily(LocalDate date, Double hours, int recordCount) {}

  public record Statistics(
      LocalDate startDate,
      LocalDate endDate,
      String sourceName,
      long periodDays,
      long validDays,
      double coverageRate,
      Double averageHours,
      double totalHours,
      int recordCount,
      LocalDateTime dataCutoff,
      List<Daily> daily) {}

  public record Page<T>(List<T> items, long total, int page, int pageSize) {}

  public record PublicUser(Long id, String username, String nickname, String role) {
    public static PublicUser of(User u) {
      return new PublicUser(u.id, u.username, u.nickname, u.role);
    }
  }
}
