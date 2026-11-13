package com.health.service;

import com.health.api.HeartRateController.Measurement;
import com.health.api.Requests.StatisticsQuery;
import com.health.model.HeartRateModels.*;
import com.health.model.Models.Page;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface HeartRateService {
  Page<Sample> records(Long userId, StatisticsQuery query);
  Statistics statistics(Long userId, StatisticsQuery query);
  List<String> sources(Long userId);
  Sample save(Long userId, Long id, Measurement measurement);
  void delete(Long userId, Long id);
  ImportResult upload(Long userId, MultipartFile file, boolean confirm);
}
