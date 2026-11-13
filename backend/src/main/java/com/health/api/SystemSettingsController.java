package com.health.api;

import com.health.model.SystemSettings;
import com.health.service.SystemSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system-settings")
public class SystemSettingsController {
  private final SystemSettingsService service;

  public SystemSettingsController(SystemSettingsService service) { this.service = service; }

  @GetMapping
  public SystemSettings settings() { return service.get(); }

  @PutMapping
  public SystemSettings save(@Valid @RequestBody Save q) {
    return service.save(new SystemSettings(q.checkinEnabled(), q.planDueEnabled(), q.reportEnabled(),
        q.dataChangeEnabled(), q.importFailedEnabled(), q.weeklyEnabled(), q.monthlyEnabled(),
        q.importAnalysisEnabled(), q.modelName().strip(), q.modelTimeoutSeconds()));
  }

  @PostMapping("/model-check")
  public Map<String, Object> check(@Valid @RequestBody Check q) {
    return service.checkConnection(q.modelName().strip(), q.modelTimeoutSeconds());
  }

  public record Save(@NotNull Boolean checkinEnabled, @NotNull Boolean planDueEnabled,
      @NotNull Boolean reportEnabled, @NotNull Boolean dataChangeEnabled, @NotNull Boolean importFailedEnabled,
      @NotNull Boolean weeklyEnabled, @NotNull Boolean monthlyEnabled, @NotNull Boolean importAnalysisEnabled,
      @NotNull @Size(max = 200) String modelName, @Min(5) @Max(90) int modelTimeoutSeconds) {}

  public record Check(@NotNull @Size(max = 200) String modelName, @Min(5) @Max(90) int modelTimeoutSeconds) {}
}
