package com.health.model;

import java.time.LocalDate;
import java.util.List;

public record CombinedAnalysis(LocalDate startDate, LocalDate endDate, long periodDays, List<Metric> metrics) {
  public record Day(LocalDate date, Number value) {}
  public record Metric(String key, String unit, String basis, Long validDays, Long recordCount,
      List<String> sources, List<Day> daily, String issue) {}
}
