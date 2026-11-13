package com.health.model;

import java.time.LocalDateTime;
import java.util.List;

public record TaskAudit(Metadata task, List<Step> steps) {
  public enum Feedback { HELPFUL, NOT_HELPFUL }

  public record Metadata(String id, String status, Long planId, Long reportId,
      Integer confirmedVersion, LocalDateTime confirmedAt, Feedback feedback, LocalDateTime feedbackAt) {}

  public record Step(Long id, String name, String status, LocalDateTime createdAt) {}
}
