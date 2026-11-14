package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.health.model.BaselineModels.Metric;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;

public final class FamilyModels {
  private FamilyModels() {}

  public record Invitation(@NotBlank @Size(max = 32) String username,
      @NotNull @Size(max = 7) Set<@NotNull Metric> metrics,
      @NotNull @Size(max = 50) Set<@NotNull @Positive Long> reportIds,
      @Min(1) @Max(365) int days) {
    @AssertTrue(message = "请至少授权一项指标或报告")
    @JsonIgnore
    public boolean isScopePresent() {
      return metrics != null && !metrics.isEmpty() || reportIds != null && !reportIds.isEmpty();
    }
  }

  public static class Grant {
    public Long id;
    @JsonIgnore public Long ownerId;
    @JsonIgnore public Long memberId;
    public String ownerName;
    public String memberName;
    public boolean incoming;
    public boolean accepted;
    public boolean expired;
    public LocalDateTime expiresAt;
    @JsonIgnore public String metricsJson;
    @JsonIgnore public String reportIdsJson;
    public Set<Metric> metrics;
    public Set<Long> reportIds;
  }
}
