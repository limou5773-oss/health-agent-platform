package com.health.service.impl;

import com.health.api.*;
import com.health.mapper.AccountMapper;
import com.health.model.Models.*;
import com.health.security.JwtTokens;
import com.health.service.AccountService;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {
  private final AccountMapper mapper;
  private final JwtTokens tokens;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  public AccountServiceImpl(AccountMapper mapper, JwtTokens tokens) {
    this.mapper = mapper;
    this.tokens = tokens;
  }

  @Transactional
  public PublicUser register(Requests.Register r) {
    User u = new User();
    u.username = r.username();
    u.nickname = r.nickname();
    u.passwordHash = bcrypt.encode(r.password());
    u.role = "USER";
    mapper.insert(u);
    mapper.insertProfile(u.id);
    return PublicUser.of(u);
  }

  public Map<String, Object> login(Requests.Login r) {
    User u = mapper.byUsername(r.username());
    if (u == null || !bcrypt.matches(r.password(), u.passwordHash))
      throw new ApiException(401, "INVALID_CREDENTIALS", "用户名或密码错误");
    if (!u.enabled) throw new ApiException(403, "ACCOUNT_DISABLED", "账号已停用");
    Instant expires = Instant.now().plusSeconds(tokens.ttl);
    return Map.of(
        "accessToken", tokens.issue(u.id, expires), "expiresAt", expires, "user", PublicUser.of(u));
  }

  public User identity(Long id) {
    User u = mapper.byId(id);
    if (u == null || !u.enabled) throw new ApiException(401, "ACCOUNT_UNAVAILABLE", "账号不可用");
    return u;
  }

  public Profile profile(Long id) {
    return mapper.profile(id);
  }

  @Transactional
  public Profile profile(Long id, Requests.Profile r) {
    Profile p = new Profile();
    p.userId = id;
    p.heightCm = r.heightCm();
    p.weightKg = r.weightKg();
    p.timezone = r.timezone();
    p.goal = r.goal();
    p.preferences = r.preferences();
    p.aiConsent = r.aiConsent();
    mapper.updateProfile(p);
    mapper.nickname(id, r.nickname());
    return mapper.profile(id);
  }

  public void password(Long id, Requests.Password r) {
    User u = mapper.byId(id);
    if (!bcrypt.matches(r.oldPassword(), u.passwordHash))
      throw new ApiException(400, "WRONG_PASSWORD", "原密码错误");
    mapper.password(id, bcrypt.encode(r.newPassword()));
  }

  public List<User> users() {
    return mapper.users();
  }

  public void status(Long actor, Long id, boolean enabled) {
    if (actor.equals(id) && !enabled) throw ApiException.conflict("不能停用当前管理员");
    if (mapper.status(id, enabled) == 0) throw ApiException.missing();
  }

  @Transactional
  public void initializeAdmin(String username, String password) {
    if (username.isBlank() && password.isBlank()) return;
    if (!username.matches("[A-Za-z0-9_]{3,32}") || !Requests.passwordBytes(password))
      throw new IllegalArgumentException("管理员环境配置无效：用户名3至32字符，密码8至72字节");
    User existing = mapper.byUsername(username);
    if (existing != null) {
      if (!"ADMIN".equals(existing.role)) throw new IllegalStateException("ADMIN_USERNAME已由普通账号使用");
      return;
    }
    User u = new User();
    u.username = username;
    u.nickname = "管理员";
    u.role = "ADMIN";
    u.passwordHash = bcrypt.encode(password);
    mapper.insert(u);
    mapper.insertProfile(u.id);
  }
}
