package com.health.api;

public class ApiException extends RuntimeException {
  public final int status;
  public final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiException missing() {
    return new ApiException(404, "NOT_FOUND", "记录不存在");
  }

  public static ApiException conflict(String message) {
    return new ApiException(409, "STATE_CONFLICT", message);
  }
}
