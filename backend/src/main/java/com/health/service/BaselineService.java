package com.health.service;

import com.health.api.BaselineRequests.Query;
import com.health.model.BaselineModels.Comparison;
import com.health.model.BaselineModels.Metric;
import com.health.model.BaselineModels.Period;
import java.time.LocalDate;

public interface BaselineService {
  Comparison compare(Long userId, Query query);
  Period summarize(Long userId, Metric metric, String source, LocalDate start, LocalDate end);
}
