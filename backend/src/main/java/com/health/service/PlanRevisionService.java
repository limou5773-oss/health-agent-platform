package com.health.service;

import com.health.api.PlanRevisionRequests.Save;
import com.health.api.PlanReviewRequests.Query;
import com.health.model.Models.Plan;
import com.health.model.PlanRevisionModels.Revision;
import com.health.model.PlanRevisionModels.Revisions;
import com.health.model.PlanReviewModels.Evidence;

public interface PlanRevisionService {
  Revisions revisions(Long userId, Long planId);
  Revision save(Long userId, Long planId, Save request);
  Revision saveSuggestion(Long userId, Long planId, Save request, long consentVersion,
      int activeVersion, boolean basedOnDraft, ReviewBasis review);
  Plan confirm(Long userId, Long planId, int version);
  void discard(Long userId, Long planId, int version);

  record ReviewBasis(Query query, Evidence evidence) {}
}
