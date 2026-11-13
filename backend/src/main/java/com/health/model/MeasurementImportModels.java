package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MeasurementImportModels {
  private MeasurementImportModels() {}

  public enum Metric {
    WEIGHT("weightKg"), HEART_RATE("bpm");

    public final String valueColumn;

    Metric(String valueColumn) {
      this.valueColumn = valueColumn;
    }
  }

  public static class Batch {
    public Long id;
    @JsonIgnore public Long userId;
    public Metric metric;
    public String status;
    public int totalRows;
    public int duplicateRows;
    public int insertedRows;
    public LocalDateTime firstMeasurement;
    public LocalDateTime lastMeasurement;
    public LocalDateTime createdAt;
    public LocalDateTime confirmedAt;
  }

  public record Row(long rowNumber, LocalDateTime measuredAt, BigDecimal value, String sourceName, String note) {}
  public record Preview(Batch batch, List<String> sources) {}
}
