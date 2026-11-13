package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.health.api.PlanRevisionRequests.Content;
import java.time.LocalDateTime;
import java.util.List;

public final class PlanRevisionModels {
  private PlanRevisionModels() {}

  public static class Revision {
    @JsonIgnore public Long planId;
    public int version;
    public String status;
    public String note;
    public LocalDateTime createdAt;
    @JsonIgnore public String contentJson;
    public Content content;
  }

  public record Revisions(int activeVersion, int latestVersion, Revision draft, List<Revision> history) {}
}
