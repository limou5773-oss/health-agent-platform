package com.health.api;

import com.health.model.MeasurementImportModels.*;
import com.health.security.Identity;
import com.health.service.MeasurementImportService;
import com.health.service.ImportAdminService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/{module:weight|heart-rate}/imports")
public class MeasurementImportController {
  private final MeasurementImportService service;
  private final ImportAdminService importAdmin;

  public MeasurementImportController(MeasurementImportService service, ImportAdminService importAdmin) {
    this.service = service;
    this.importAdmin = importAdmin;
  }

  @PostMapping(value = "/preview", consumes = "multipart/form-data")
  public Preview upload(HttpServletRequest request, @PathVariable String module, @RequestPart MultipartFile file,
      @RequestParam(defaultValue = "CSV") Format format) {
    Long userId = Identity.user(request).id;
    Metric metric = metric(module);
    try {
      return format == Format.APPLE_HEALTH ? service.previewApple(userId, metric, file) : service.preview(userId, metric, file);
    } catch (ApiException e) {
      if (e.status == 400 || "MEASUREMENT_CONFLICT".equals(e.code))
        importAdmin.rejected(metric.importKind, userId, e.code);
      throw e;
    }
  }

  public enum Format { CSV, APPLE_HEALTH }

  @GetMapping("/{id}")
  public Preview preview(HttpServletRequest request, @PathVariable String module, @PathVariable Long id) {
    return service.preview(Identity.user(request).id, metric(module), id);
  }

  @PostMapping("/{id}/confirm")
  public Batch confirm(HttpServletRequest request, @PathVariable String module, @PathVariable Long id) {
    Metric metric = metric(module);
    Long userId = Identity.user(request).id;
    try {
      return service.confirm(userId, metric, id);
    } catch (ApiException e) {
      if ("MEASUREMENT_CONFLICT".equals(e.code)) importAdmin.recordIssue(metric.importKind, userId, id, e.code);
      throw e;
    }
  }

  @GetMapping
  public List<Batch> history(HttpServletRequest request, @PathVariable String module) {
    return service.history(Identity.user(request).id, metric(module));
  }

  private static Metric metric(String module) {
    return module.equals("weight") ? Metric.WEIGHT : Metric.HEART_RATE;
  }
}
