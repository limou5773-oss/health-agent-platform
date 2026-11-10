package com.health.security;

import com.health.api.ApiException;
import com.health.model.Models.User;
import com.health.service.AccountService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final JwtTokens tokens;
  private final AccountService accounts;

  public AuthInterceptor(JwtTokens tokens, AccountService accounts) {
    this.tokens = tokens;
    this.accounts = accounts;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer "))
      throw new ApiException(401, "UNAUTHENTICATED", "请登录后操作");
    Long id;
    try {
      id = tokens.subject(header.substring(7));
    } catch (JwtException | IllegalArgumentException e) {
      throw new ApiException(401, "INVALID_TOKEN", "登录已失效，请重新登录");
    }
    User user = accounts.identity(id);
    if (request.getRequestURI().startsWith("/api/admin/") && !"ADMIN".equals(user.role))
      throw new ApiException(403, "FORBIDDEN", "需要管理员权限");
    request.setAttribute(Identity.KEY, user);
    return true;
  }
}
