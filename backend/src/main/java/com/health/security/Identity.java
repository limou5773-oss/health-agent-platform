package com.health.security;

import com.health.model.Models.User;
import jakarta.servlet.http.HttpServletRequest;

public final class Identity {
  public static final String KEY = Identity.class.getName();

  private Identity() {}

  public static User user(HttpServletRequest request) {
    return (User) request.getAttribute(KEY);
  }
}
