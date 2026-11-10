package com.health.api;

import com.health.model.Models.*;
import com.health.security.Identity;
import com.health.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {
  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public PublicUser register(@Valid @RequestBody Requests.Register r) {
    return service.register(r);
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@Valid @RequestBody Requests.Login r) {
    return service.login(r);
  }

  @GetMapping("/auth/me")
  public PublicUser me(HttpServletRequest r) {
    return PublicUser.of(Identity.user(r));
  }

  @PutMapping("/auth/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(HttpServletRequest r, @Valid @RequestBody Requests.Password body) {
    service.password(Identity.user(r).id, body);
  }

  @GetMapping("/profile")
  public Profile profile(HttpServletRequest r) {
    return service.profile(Identity.user(r).id);
  }

  @PutMapping("/profile")
  public Profile profile(HttpServletRequest r, @Valid @RequestBody Requests.Profile body) {
    return service.profile(Identity.user(r).id, body);
  }
}
