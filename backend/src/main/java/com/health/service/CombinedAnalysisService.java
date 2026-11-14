package com.health.service;

import com.health.model.CombinedAnalysis;
import com.health.model.Models.Task;
import com.health.model.Models.Profile;

public interface CombinedAnalysisService {
  CombinedAnalysis statistics(Task task, Profile profile);
}
