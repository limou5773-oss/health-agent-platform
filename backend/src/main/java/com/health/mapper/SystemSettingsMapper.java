package com.health.mapper;

import com.health.model.SystemSettings;

public interface SystemSettingsMapper {
  SystemSettings get();
  void save(SystemSettings settings);
}
