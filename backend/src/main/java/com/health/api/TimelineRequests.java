package com.health.api;

import com.health.model.TimelineModels.EventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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

  public static class DeleteQuery extends Query {
    @Override
    @NotNull(message = "请选择要删除的健康记录类型")
    public EventType getType() {
      return super.getType();
    }
  }

  public static class ConfirmDelete extends DeleteQuery {
    @NotNull
    @Pattern(regexp = "[a-f0-9]{64}")
    private String fingerprint;

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String value) { fingerprint = value; }
  }
}
