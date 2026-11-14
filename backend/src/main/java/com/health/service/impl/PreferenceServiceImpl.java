package com.health.service.impl;

import com.health.mapper.PreferenceMapper;
import com.health.mapper.AccountMapper;
import com.health.security.Identity;
import com.health.model.PreferenceModels.Kind;
import com.health.model.PreferenceModels.Preference;
import com.health.service.PreferenceService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceServiceImpl implements PreferenceService {
  private final PreferenceMapper mapper;
  private final AccountMapper accounts;

  public PreferenceServiceImpl(PreferenceMapper mapper, AccountMapper accounts) {
    this.mapper = mapper;
    this.accounts = accounts;
  }

  @Override
  public List<Preference> list(Long userId) {
    return mapper.list(userId);
  }

  @Override
  @Transactional
  public Preference save(Long userId, Kind kind, String content) {
    Identity.requireExisting(accounts.lockUser(userId));
    String value = content.strip();
    mapper.save(userId, kind, value);
    return new Preference(kind, value);
  }

  @Override
  public void delete(Long userId, Kind kind) {
    mapper.delete(userId, kind);
  }
}
