package com.health.api;

import com.health.model.TriggerModels.*;
import com.health.security.Identity;
import com.health.service.TriggerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/triggers")
public class TriggerController {
  private final TriggerService service;

  public TriggerController(TriggerService service) {
    this.service = service;
  }

  @GetMapping("/settings")
  public Settings settings(HttpServletRequest request) {
    return service.settings(Identity.user(request).id);
  }

  @PutMapping("/settings")
  public Settings save(HttpServletRequest request, @Valid @RequestBody Save body) {
    String source = body.sourceName() == null || body.sourceName().isBlank() ? null : body.sourceName();
    return service.save(Identity.user(request).id, new Settings(body.weeklyEnabled(), body.importEnabled(),
        body.importDays(), source, body.notifyInApp()));
  }

  @GetMapping("/runs")
  public List<Run> runs(HttpServletRequest request) {
    return service.runs(Identity.user(request).id);
  }

  public record Save(@NotNull Boolean weeklyEnabled, @NotNull Boolean importEnabled,
      @NotNull @Min(1) @Max(31) Integer importDays, @Size(max = 100) String sourceName,
      @NotNull Boolean notifyInApp) {}
}
