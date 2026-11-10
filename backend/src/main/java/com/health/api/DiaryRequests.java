package com.health.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

public final class DiaryRequests {
  private DiaryRequests() {}

  public record Entry(
      @NotNull LocalDateTime recordedAt,
      @NotNull @Size(max = 40) String mood,
      @DecimalMin("0") @DecimalMax("10") BigDecimal stressLevel,
      @NotNull @Size(max = 500) String bodyFeeling,
      @NotNull @Size(max = 1000) String events,
      @NotNull @Size(max = 200) String tags,
      @NotNull @Size(max = 2000) String note,
      @NotBlank @Size(max = 100) String sourceName,
      @NotNull Boolean agentAllowed) {
    public Entry {
      if (recordedAt != null) recordedAt = recordedAt.truncatedTo(ChronoUnit.SECONDS);
    }

    @AssertTrue(message = "压力值须为0至10的整数")
    @JsonIgnore
    public boolean isStressInteger() {
      return stressLevel == null || stressLevel.stripTrailingZeros().scale() <= 0;
    }

    @AssertTrue(message = "请填写至少一项自述文字或压力值")
    @JsonIgnore
    public boolean isContentPresent() {
      return stressLevel != null
          || Stream.of(mood, bodyFeeling, events, tags, note)
              .anyMatch(text -> text != null && !text.isBlank());
    }
  }

  public record Permission(@NotNull Boolean agentAllowed) {}

  public static class Query extends Requests.SleepQuery {
    @Size(max = 100) public String query;
    public Boolean agentAllowed;

    public String getQuery() { return query; }
    public void setQuery(String value) { query = value; }
    public Boolean getAgentAllowed() { return agentAllowed; }
    public void setAgentAllowed(Boolean value) { agentAllowed = value; }
  }
}
