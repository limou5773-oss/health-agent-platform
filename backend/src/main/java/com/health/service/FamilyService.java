package com.health.service;

import com.health.api.Requests.StatisticsQuery;
import com.health.model.BaselineModels.Metric;
import com.health.model.BaselineModels.Period;
import com.health.model.FamilyModels.Grant;
import com.health.model.FamilyModels.Invitation;
import com.health.model.Models.Report;
import java.util.List;

public interface FamilyService {
  List<Grant> list(Long userId);
  void invite(Long userId, Invitation request);
  void accept(Long userId, Long id);
  void revoke(Long userId, Long id);
  Period statistics(Long userId, Long id, Metric metric, StatisticsQuery query);
  Report report(Long userId, Long id, Long reportId);
}
