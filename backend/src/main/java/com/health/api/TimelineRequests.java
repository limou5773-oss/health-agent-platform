package com.health.api;

import com.health.model.TimelineModels.EventType;

public final class TimelineRequests {
  private TimelineRequests() {}

  public static class Query extends Requests.StatisticsQuery {
    private EventType type;

    public EventType getType() {
      return type;
    }

    public void setType(EventType type) {
      this.type = type;
    }
  }
}
