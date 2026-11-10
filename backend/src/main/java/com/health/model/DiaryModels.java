package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DiaryModels {
  private DiaryModels() {}

  public static class DiaryRecord {
    public Long id;
    @JsonIgnore public Long userId;
    public LocalDateTime recordedAt;
    public String mood;
    public Integer stressLevel;
    public String bodyFeeling;
    public String events;
    public String tags;
    public String note;
    public String sourceName;
    public boolean agentAllowed;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
  }

  public record DiaryEntry(
      Long id, LocalDateTime recordedAt, String mood, Integer stressLevel,
      String bodyFeeling, String events, String tags, String note, String sourceName) {}

  public record DiaryContext(
      LocalDate startDate, LocalDate endDate, int limit, long totalCount,
      int returnedCount, boolean truncated, List<DiaryEntry> entries) {}
}
