package com.health.service.impl;

import com.health.security.Identity;
import com.health.api.*;
import com.health.mapper.AccountMapper;
import com.health.model.Models.*;
import com.health.security.JwtTokens;
import com.health.service.AccountService;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {
  private final AccountMapper mapper;
  private final JwtTokens tokens;
  private final String initialAdminUsername;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  public AccountServiceImpl(AccountMapper mapper, JwtTokens tokens,
      @Value("${app.admin-username}") String initialAdminUsername) {
    this.mapper = mapper;
    this.tokens = tokens;
    this.initialAdminUsername = initialAdminUsername;
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
    Identity.requireExisting(mapper.lockUser(id));
    Profile p = mapper.profile(id);
    if ((p.aiConsent && Boolean.FALSE.equals(r.aiConsent()))
        || (p.aiSleep && Boolean.FALSE.equals(r.aiSleep()))
        || (p.aiActivity && Boolean.FALSE.equals(r.aiActivity()))
        || (p.aiWeight && Boolean.FALSE.equals(r.aiWeight()))
        || (p.aiHeartRate && Boolean.FALSE.equals(r.aiHeartRate()))
        || (p.aiDiary && Boolean.FALSE.equals(r.aiDiary()))) p.aiConsentVersion++;
    p.heightCm = r.heightCm();
    p.weightKg = r.weightKg();
    p.timezone = r.timezone();
    p.goal = r.goal();
    p.preferences = r.preferences();
    if (r.availableTime() != null) p.availableTime = r.availableTime().strip();
    if (r.limitations() != null) p.limitations = r.limitations().strip();
    p.aiConsent = r.aiConsent();
    if (r.aiSleep() != null) p.aiSleep = r.aiSleep();
    if (r.aiActivity() != null) p.aiActivity = r.aiActivity();
    if (r.aiWeight() != null) p.aiWeight = r.aiWeight();
    if (r.aiHeartRate() != null) p.aiHeartRate = r.aiHeartRate();
    if (r.aiDiary() != null) p.aiDiary = r.aiDiary();
    mapper.updateProfile(p);
    mapper.nickname(id, r.nickname());
    return mapper.profile(id);
  }

  @Transactional
  public void password(Long id, Requests.Password r) {
    Identity.requireExisting(mapper.lockUser(id));
    User u = mapper.byId(id);
    if (!bcrypt.matches(r.oldPassword(), u.passwordHash))
      throw new ApiException(400, "WRONG_PASSWORD", "原密码错误");
    mapper.password(id, bcrypt.encode(r.newPassword()));
  }

  public List<User> users() {
    return mapper.users();
  }

  @Transactional
  public void status(Long actor, Long id, boolean enabled) {
    User target = lockAdminChange(actor, id);
    if (actor.equals(id) && !enabled) throw ApiException.conflict("不能停用当前管理员");
    if (target.enabled == enabled) return;
    mapper.status(id, enabled);
    mapper.insertAccountChange(actor, id, "STATUS", Boolean.toString(target.enabled), Boolean.toString(enabled));
  }

  @Transactional
  public void role(Long actor, Long id, UserRole role) {
    User target = lockAdminChange(actor, id);
    if (target.role.equals(role.name())) return;
    if (actor.equals(id) || initialAdminUsername.equalsIgnoreCase(target.username))
      throw ApiException.conflict("不能更改当前账号或初始化管理员的角色");
    mapper.role(id, role);
    mapper.insertAccountChange(actor, id, "ROLE", target.role, role.name());
  }

  public List<AccountChange> accountChanges() {
    return mapper.accountChanges();
  }

  private User lockAdminChange(Long actorId, Long targetId) {
    List<User> accounts = mapper.lockAdminAccounts(actorId, targetId);
    User actor = accounts.stream().filter(user -> actorId.equals(user.id)).findFirst().orElse(null);
    if (actor == null || !actor.enabled || !"ADMIN".equals(actor.role))
      throw new ApiException(403, "FORBIDDEN", "需要有效的管理员权限");
    return accounts.stream().filter(user -> targetId.equals(user.id)).findFirst().orElseThrow(ApiException::missing);
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
