package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class HeartRateModels {
  private HeartRateModels() {}

  public static class Sample {
    public Long id;
    @JsonIgnore public Long userId;
    public LocalDateTime measuredAt;
    public BigDecimal bpm;
    public String sourceName;
    public String note;
    public boolean corrected;
  }

  public record Daily(LocalDate date, long recordCount, BigDecimal averageBpm) {}
  public record Statistics(long periodDays, long validDays, long recordCount,
      BigDecimal averageBpm, LocalDateTime dataCutoff, List<Daily> daily) {}
  public record Totals(long recordCount, BigDecimal averageBpm, LocalDateTime dataCutoff) {}
  public record ImportResult(int newRecords, int duplicates, LocalDateTime firstMeasurement,
      LocalDateTime lastMeasurement, List<String> sources) {}
}
