package com.health.service;

import com.health.model.TriggerModels.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TriggerService {
  Settings settings(Long userId);
  Settings save(Long userId, Settings settings);
  List<Run> runs(Long userId);
  void sleepImported(Long userId, Long batchId, Map<String, LocalDate> latestBySource);
}
