package com.health.service;

import com.health.api.BaselineRequests.Query;
import com.health.model.BaselineModels.Comparison;

public interface BaselineService {
  Comparison compare(Long userId, Query query);
}
