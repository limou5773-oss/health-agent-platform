package com.health.model;

import java.time.LocalDateTime;
import java.util.List;

public final class ImportAdminModels {
  private ImportAdminModels() {}

  public enum Kind {
    SLEEP(List.of("startTime", "endTime", "sourceName", "note")),
    STEPS(List.of("startTime", "endTime", "steps", "mode", "sourceName", "note")),
    WEIGHT(List.of("measuredAt", "weightKg", "sourceName", "note")),
    HEART_RATE(List.of("measuredAt", "bpm", "sourceName", "note"));

    public final List<String> headers;

    Kind(List<String> headers) { this.headers = headers; }

    public boolean matches(List<String> actual) {
      return actual.size() == headers.size()
          && actual.getFirst().replace("\uFEFF", "").equals(headers.getFirst())
          && actual.subList(1, actual.size()).equals(headers.subList(1, headers.size()));
    }
  }

  public record Template(Kind kind, List<String> headers, String instructions) {}
  public record Batch(Kind kind, Long id, Long userId, String format, String status,
      int totalRows, int validRows, int invalidRows, int duplicateRows, int unsupportedRows,
      int insertedRows, LocalDateTime createdAt) {}
  public record Issue(String reason, int issueRows) {}
}
