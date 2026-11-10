package com.health.service;

import com.health.api.Requests.SleepQuery;
import com.health.api.WeightRequests;
import com.health.model.Models.Page;
import com.health.model.WeightModels.*;
import java.time.LocalDate;
import java.util.List;

public interface WeightService {
  Page<WeightRecord> records(Long uid, SleepQuery query);
  WeightRecord record(Long uid, Long id);
  WeightRecord createRecord(Long uid, WeightRequests.Measurement request);
  WeightRecord updateRecord(Long uid, Long id, WeightRequests.Measurement request);
  void deleteRecord(Long uid, Long id);
  List<String> sources(Long uid);
  WeightStatistics statistics(Long uid, LocalDate start, LocalDate end, String source);
  WeightGoal goal(Long uid);
  WeightGoal saveGoal(Long uid, WeightRequests.Goal request);
  void deleteGoal(Long uid);
  WeightGoalProgress goalProgress(Long uid);
}
