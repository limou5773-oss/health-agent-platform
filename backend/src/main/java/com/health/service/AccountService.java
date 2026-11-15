package com.health.service;

import com.health.api.Requests;
import com.health.model.Models.*;
import java.util.List;
import java.util.Map;

public interface AccountService {
  PublicUser register(Requests.Register request);

  Map<String, Object> login(Requests.Login request);

  User identity(Long id);

  Profile profile(Long id);

  Profile profile(Long id, Requests.Profile request);

  void password(Long id, Requests.Password request);

  List<User> users();

  void status(Long actor, Long id, boolean enabled);

  void role(Long actor, Long id, UserRole role);

  List<AccountChange> accountChanges();

  void initializeAdmin(String username, String password);
}
