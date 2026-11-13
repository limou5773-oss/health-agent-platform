package com.health.service;

import com.health.api.HeartRateController.Measurement;
import com.health.api.Requests.StatisticsQuery;
import com.health.model.HeartRateModels.*;
import com.health.model.Models.Page;
import java.time.LocalDate;
import java.util.List;

public interface HeartRateService {
  Page<Sample> records(Long userId, StatisticsQuery query);
  Statistics statistics(Long userId, LocalDate start, LocalDate end, String source);
  List<String> sources(Long userId);
  Sample save(Long userId, Long id, Measurement measurement);
  void delete(Long userId, Long id);
}
