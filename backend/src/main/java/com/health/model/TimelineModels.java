package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TimelineModels {
  private TimelineModels() {}

  public enum EventType {
    SLEEP, STEPS, WORKOUT, MEAL, WATER, WEIGHT, HEART_RATE, DIARY, CHECKIN, PLAN_CREATED, PLAN_STATUS
  }

  public record DeletionSource(String sourceName, int count) {}

  public record DeletionPreview(int count, String fingerprint, String impact, List<DeletionSource> sources) {}

  public static class Event {
    public EventType type;
    public Long id;
    public LocalDate eventDate;
    public LocalDateTime occurredAt;
    public String sourceName;
    public String summary;
    @JsonIgnore public String detailsJson;
    @JsonInclude(JsonInclude.Include.NON_NULL) public JsonNode details;
  }
}
