package com.health.service;

import com.health.api.HealthReportRequests.Generate;
import com.health.model.Models.Report;

public interface HealthReportService {
  Report generate(Long userId, Generate request);
}
