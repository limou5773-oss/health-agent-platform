package com.health.api;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e) {
    return error(e.status, e.code, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return error(
        400, "INVALID_INPUT", e.getBindingResult().getAllErrors().getFirst().getDefaultMessage());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    ServletRequestBindingException.class,
    MethodArgumentTypeMismatchException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return error(400, "INVALID_INPUT", "请求字段或格式无效");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> upload(Exception e) {
    return error(413, "FILE_TOO_LARGE", "CSV不能超过10MB");
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return error(409, "DUPLICATE_RECORD", "记录已存在");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    log.error("Unhandled request failure", e);
    return error(500, "INTERNAL_ERROR", "服务器处理失败，请联系管理员");
  }

  private ResponseEntity<?> error(int status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
  }
}
