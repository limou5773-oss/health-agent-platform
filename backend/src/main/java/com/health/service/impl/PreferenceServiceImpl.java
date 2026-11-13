package com.health.service.impl;

import com.health.mapper.PreferenceMapper;
import com.health.model.PreferenceModels.Kind;
import com.health.model.PreferenceModels.Preference;
import com.health.service.PreferenceService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PreferenceServiceImpl implements PreferenceService {
  private final PreferenceMapper mapper;

  public PreferenceServiceImpl(PreferenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<Preference> list(Long userId) {
    return mapper.list(userId);
  }

  @Override
  public Preference save(Long userId, Kind kind, String content) {
    String value = content.strip();
    mapper.save(userId, kind, value);
    return new Preference(kind, value);
  }

  @Override
  public void delete(Long userId, Kind kind) {
    mapper.delete(userId, kind);
  }
}
