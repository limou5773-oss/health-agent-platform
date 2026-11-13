package com.health.api;

import com.health.model.Models.*;
import com.health.security.Identity;
import com.health.service.HealthReportService;
import com.health.service.PlanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PlanController {
  private final PlanService service;
  private final HealthReportService healthReports;

  public PlanController(PlanService service, HealthReportService healthReports) {
    this.service = service;
    this.healthReports = healthReports;
  }

  @GetMapping("/plans")
  public List<Plan> list(HttpServletRequest r) {
    return service.list(Identity.user(r).id);
  }

  @GetMapping("/plans/{id}")
  public Plan get(HttpServletRequest r, @PathVariable Long id) {
    return service.get(Identity.user(r).id, id);
  }

  @PatchMapping("/plans/{id}/status")
  public Plan status(
      HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Requests.PlanStatus q) {
    return service.status(Identity.user(r).id, id, q.status());
  }

  @PostMapping("/plan-items/{id}/checkins")
  public Map<String, Object> checkin(
      HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Requests.Checkin q) {
    return service.checkin(Identity.user(r).id, id, q.date());
  }

  @DeleteMapping("/plan-items/{id}/checkins")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void uncheck(HttpServletRequest r, @PathVariable Long id) {
    service.uncheck(Identity.user(r).id, id);
  }

  @GetMapping("/reports")
  public List<Report> reports(HttpServletRequest r) {
    return service.reports(Identity.user(r).id);
  }

  @PostMapping("/reports/health")
  @ResponseStatus(HttpStatus.CREATED)
  public Report generateHealthReport(HttpServletRequest r, @Valid @RequestBody HealthReportRequests.Generate q) {
    return healthReports.generate(Identity.user(r).id, q);
  }

  @GetMapping("/reports/{id}")
  public Report report(HttpServletRequest r, @PathVariable Long id) {
    return service.report(Identity.user(r).id, id);
  }
}
