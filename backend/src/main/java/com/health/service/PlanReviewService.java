package com.health.service;

import com.health.api.PlanReviewRequests.Query;
import com.health.api.PlanReviewRequests.OutcomeQuery;
import com.health.model.PlanReviewModels.Review;
import com.health.model.PlanReviewModels.Outcome;

public interface PlanReviewService {
  Review review(Long userId, Long planId, Query query);
  Outcome outcome(Long userId, Long planId, OutcomeQuery query);
  void missedReason(Long userId, Long itemId, String reason);
}
