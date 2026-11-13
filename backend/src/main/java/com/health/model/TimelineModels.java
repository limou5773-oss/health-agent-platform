package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TimelineModels {
  private TimelineModels() {}

  public enum EventType {
    SLEEP, STEPS, WORKOUT, MEAL, WATER, WEIGHT, DIARY, CHECKIN, PLAN_CREATED, PLAN_STATUS
  }

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
