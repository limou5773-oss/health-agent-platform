package com.health.service;

import com.health.model.PreferenceModels.Kind;
import com.health.model.PreferenceModels.Preference;
import java.util.List;

public interface PreferenceService {
  List<Preference> list(Long userId);
  Preference save(Long userId, Kind kind, String content);
  void delete(Long userId, Kind kind);
}
