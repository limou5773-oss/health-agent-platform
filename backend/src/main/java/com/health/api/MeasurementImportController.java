package com.health.api;

import com.health.model.MeasurementImportModels.*;
import com.health.security.Identity;
import com.health.service.MeasurementImportService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/{module:weight|heart-rate}/imports")
public class MeasurementImportController {
  private final MeasurementImportService service;

  public MeasurementImportController(MeasurementImportService service) {
    this.service = service;
  }

  @PostMapping(value = "/preview", consumes = "multipart/form-data")
  public Preview upload(HttpServletRequest request, @PathVariable String module, @RequestPart MultipartFile file) {
    return service.preview(Identity.user(request).id, metric(module), file);
  }

  @GetMapping("/{id}")
  public Preview preview(HttpServletRequest request, @PathVariable String module, @PathVariable Long id) {
    return service.preview(Identity.user(request).id, metric(module), id);
  }

  @PostMapping("/{id}/confirm")
  public Batch confirm(HttpServletRequest request, @PathVariable String module, @PathVariable Long id) {
    return service.confirm(Identity.user(request).id, metric(module), id);
  }

  @GetMapping
  public List<Batch> history(HttpServletRequest request, @PathVariable String module) {
    return service.history(Identity.user(request).id, metric(module));
  }

  private static Metric metric(String module) {
    return module.equals("weight") ? Metric.WEIGHT : Metric.HEART_RATE;
  }
}
