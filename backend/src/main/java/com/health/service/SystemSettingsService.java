package com.health.service;

import com.health.model.SystemSettings;
import java.util.Map;

public interface SystemSettingsService {
  SystemSettings get();
  SystemSettings save(SystemSettings settings);
  Map<String, Object> checkConnection(String modelName, int timeoutSeconds);
}
