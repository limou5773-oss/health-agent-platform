package com.health.service;

import com.health.api.PlanRevisionRequests.Suggest;
import com.health.model.PlanRevisionModels.Revision;

public interface PlanRevisionAgentService {
  Revision suggest(Long userId, Long planId, Suggest request);
}
