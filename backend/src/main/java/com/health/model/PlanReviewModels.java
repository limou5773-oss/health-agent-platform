package com.health.model;

import com.health.model.BaselineModels.Comparison;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class PlanReviewModels {
  private PlanReviewModels() {}

  public record Item(Long id, LocalDate date, LocalTime time, String title,
      boolean completed, String missedReason) {}

  public record Review(int planVersion, LocalDate today, int scheduled, int completed,
      BigDecimal completionRate, List<Item> missed, Comparison comparison) {}
}
