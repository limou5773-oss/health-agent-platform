package com.health.service;

import com.health.api.PlanReviewRequests.Query;
import com.health.model.PlanReviewModels.Review;

public interface PlanReviewService {
  Review review(Long userId, Long planId, Query query);
  void missedReason(Long userId, Long itemId, String reason);
}
