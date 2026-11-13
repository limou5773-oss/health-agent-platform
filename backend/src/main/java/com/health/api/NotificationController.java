package com.health.api;

import com.health.model.Models.Page;
import com.health.model.NotificationModels.*;
import com.health.security.Identity;
import com.health.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  public Page<Message> messages(HttpServletRequest request,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return service.messages(Identity.user(request).id, page, pageSize, unreadOnly);
  }

  @GetMapping("/unread-count")
  public Map<String, Long> unread(HttpServletRequest request) {
    return Map.of("count", service.unread(Identity.user(request).id));
  }

  @PutMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(HttpServletRequest request, @PathVariable Long id) {
    service.read(Identity.user(request).id, id);
  }

  @PutMapping("/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void readAll(HttpServletRequest request) {
    service.readAll(Identity.user(request).id);
  }

  @GetMapping("/settings")
  public Settings settings(HttpServletRequest request) {
    return service.settings(Identity.user(request).id);
  }

  @PutMapping("/settings")
  public Settings saveSettings(HttpServletRequest request, @Valid @RequestBody SettingsRequest body) {
    return service.saveSettings(Identity.user(request).id, new Settings(body.checkinEnabled(),
        body.checkinFrequency(), body.dailyTime(), body.planDueEnabled(), body.reportEnabled(),
        body.dataChangeEnabled(), body.importFailedEnabled()));
  }

  public record SettingsRequest(@NotNull Boolean checkinEnabled, @NotNull CheckinFrequency checkinFrequency,
      @NotNull LocalTime dailyTime, @NotNull Boolean planDueEnabled, @NotNull Boolean reportEnabled,
      @NotNull Boolean dataChangeEnabled, @NotNull Boolean importFailedEnabled) {}
}
