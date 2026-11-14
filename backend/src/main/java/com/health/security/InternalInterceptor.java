package com.health.security;

import com.health.api.*;
import com.health.model.Models.*;
import com.health.service.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;

@Component
public class InternalInterceptor implements HandlerInterceptor {
  private final byte[] key;
  private final TaskService tasks;
  private final AccountService accounts;

  public InternalInterceptor(
      @Value("${app.internal-service-key}") String key,
      TaskService tasks,
      AccountService accounts) {
    this.key = key.getBytes(StandardCharsets.UTF_8);
    this.tasks = tasks;
    this.accounts = accounts;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String supplied = request.getHeader("X-Service-Key");
    if (supplied == null || !MessageDigest.isEqual(key, supplied.getBytes(StandardCharsets.UTF_8)))
      throw new ApiException(401, "INVALID_SERVICE_KEY", "服务认证失败");
    @SuppressWarnings("unchecked")
    Map<String, String> vars =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    Task task = tasks.internalIdentity(vars.get("id"), request.getHeader("X-Task-Token"));
    User user = accounts.identity(task.userId);
    Profile p = accounts.profile(task.userId);
    AgentPermissions.requireCurrent(p, task);
    if ("COMPLETED".equals(task.status) && request.getRequestURI().contains("/tools/"))
      throw new ApiException(403, "TASK_INACTIVE", "任务已完成");
    request.setAttribute(Identity.KEY, user);
    request.setAttribute(InternalController.TASK, task);
    request.setAttribute(InternalController.PROFILE, p);
    return true;
  }
}
