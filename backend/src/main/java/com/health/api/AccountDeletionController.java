package com.health.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.health.security.Identity;
import com.health.service.AccountDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountDeletionController {
  private final AccountDeletionService service;

  public AccountDeletionController(AccountDeletionService service) {
    this.service = service;
  }

  @DeleteMapping("/api/profile/account")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest request, @Valid @RequestBody Confirmation confirmation) {
    service.delete(Identity.user(request).id, confirmation.password());
  }

  public record Confirmation(@NotBlank @Size(max = 72) String password) {
    @AssertTrue(message = "密码须为8至72个UTF-8字节")
    @JsonIgnore
    public boolean isPasswordValid() {
      return password == null || Requests.passwordBytes(password);
    }
  }
}
