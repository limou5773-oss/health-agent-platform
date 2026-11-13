package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.health.model.ImportAdminModels.Kind;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MeasurementImportModels {
  private MeasurementImportModels() {}

  public enum Metric {
    WEIGHT(Kind.WEIGHT), HEART_RATE(Kind.HEART_RATE);

    public final Kind importKind;

    Metric(Kind importKind) {
      this.importKind = importKind;
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
