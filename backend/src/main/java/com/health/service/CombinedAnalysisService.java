package com.health.service;

import com.health.model.CombinedAnalysis;
import com.health.model.Models.Task;

public interface CombinedAnalysisService {
  CombinedAnalysis statistics(Task task);
}
