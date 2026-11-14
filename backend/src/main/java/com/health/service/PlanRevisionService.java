package com.health.service;

import com.health.api.PlanRevisionRequests.Save;
import com.health.model.Models.Plan;
import com.health.model.PlanRevisionModels.Revision;
import com.health.model.PlanRevisionModels.Revisions;

public interface PlanRevisionService {
  Revisions revisions(Long userId, Long planId);
  Revision save(Long userId, Long planId, Save request);
  Revision saveSuggestion(Long userId, Long planId, Save request, long consentVersion,
      int activeVersion, boolean basedOnDraft);
  Plan confirm(Long userId, Long planId, int version);
  void discard(Long userId, Long planId, int version);
}
