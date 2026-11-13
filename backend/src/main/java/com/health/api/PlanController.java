package com.health.api;

import com.health.model.Models.*;
import com.health.security.Identity;
import com.health.service.HealthReportService;
import com.health.service.PlanService;
import com.health.service.PlanRevisionService;
import com.health.service.PlanReviewService;
import com.health.model.PlanReviewModels.Review;
import com.health.model.PlanRevisionModels.Revision;
import com.health.model.PlanRevisionModels.Revisions;
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
  private final PlanRevisionService revisions;
  private final PlanReviewService reviews;

  public PlanController(PlanService service, HealthReportService healthReports,
      PlanRevisionService revisions, PlanReviewService reviews) {
    this.service = service;
    this.healthReports = healthReports;
    this.revisions = revisions;
    this.reviews = reviews;
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

  @GetMapping("/plans/{id}/revisions")
  public Revisions revisions(HttpServletRequest r, @PathVariable Long id) {
    return revisions.revisions(Identity.user(r).id, id);
  }

  @GetMapping("/plans/{id}/review")
  public Review review(HttpServletRequest r, @PathVariable Long id,
      @Valid @ModelAttribute PlanReviewRequests.Query query) {
    return reviews.review(Identity.user(r).id, id, query);
  }

  @PutMapping("/plan-items/{id}/missed-reason")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void missedReason(HttpServletRequest r, @PathVariable Long id,
      @Valid @RequestBody PlanReviewRequests.Reason body) {
    reviews.missedReason(Identity.user(r).id, id, body.reason());
  }

  @PostMapping("/plans/{id}/revisions")
  @ResponseStatus(HttpStatus.CREATED)
  public Revision saveRevision(HttpServletRequest r, @PathVariable Long id,
      @Valid @RequestBody PlanRevisionRequests.Save q) {
    return revisions.save(Identity.user(r).id, id, q);
  }

  @PostMapping("/plans/{id}/revisions/{version}/confirm")
  public Plan confirmRevision(HttpServletRequest r, @PathVariable Long id, @PathVariable int version) {
    return revisions.confirm(Identity.user(r).id, id, version);
  }

  @DeleteMapping("/plans/{id}/revisions/{version}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void discardRevision(HttpServletRequest r, @PathVariable Long id, @PathVariable int version) {
    revisions.discard(Identity.user(r).id, id, version);
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

  @PostMapping("/reports/focused")
  @ResponseStatus(HttpStatus.CREATED)
  public Report generateFocusedReport(HttpServletRequest r, @Valid @RequestBody HealthReportRequests.Focused q) {
    return healthReports.focused(Identity.user(r).id, q);
  }

  @GetMapping("/reports/{id}")
  public Report report(HttpServletRequest r, @PathVariable Long id) {
    return service.report(Identity.user(r).id, id);
  }
}
