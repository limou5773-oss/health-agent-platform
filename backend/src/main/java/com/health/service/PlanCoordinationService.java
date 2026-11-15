package com.health.service;

import com.health.api.PlanCoordinationRequest;
import com.health.api.PlanRevisionRequests.Content;
import java.util.List;

public interface PlanCoordinationService {
  Preview preview(Long userId, Long planId, PlanCoordinationRequest request);

  record Conflict(long itemId, String title, String scheduledAt, long priorityPlanId,
      long priorityItemId, String priorityTitle, String priorityAt, String reason, String suggestion, long planId) {}

  record Preview(int planVersion, int latestVersion, List<Conflict> conflicts, List<Conflict> priorityConflicts,
      Content draft, String note) {}
}
