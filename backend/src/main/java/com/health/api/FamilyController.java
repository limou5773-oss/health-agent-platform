package com.health.api;

import com.health.model.BaselineModels.Metric;
import com.health.model.BaselineModels.Period;
import com.health.model.FamilyModels.Grant;
import com.health.model.FamilyModels.Invitation;
import com.health.model.Models.Report;
import com.health.security.Identity;
import com.health.service.FamilyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/family/grants")
public class FamilyController {
  private final FamilyService service;

  public FamilyController(FamilyService service) {
    this.service = service;
  }

  @GetMapping
  public List<Grant> list(HttpServletRequest r) {
    return service.list(Identity.user(r).id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void invite(HttpServletRequest r, @RequestBody @Valid Invitation request) {
    service.invite(Identity.user(r).id, request);
  }

  @PostMapping("/{id}/accept")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void accept(HttpServletRequest r, @PathVariable Long id) {
    service.accept(Identity.user(r).id, id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(HttpServletRequest r, @PathVariable Long id) {
    service.revoke(Identity.user(r).id, id);
  }

  @GetMapping("/{id}/statistics/{metric}")
  public ResponseEntity<Period> statistics(HttpServletRequest r, @PathVariable Long id, @PathVariable Metric metric,
      @Valid @ModelAttribute Requests.StatisticsQuery query) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.statistics(Identity.user(r).id, id, metric, query));
  }

  @GetMapping("/{id}/reports/{reportId}")
  public ResponseEntity<Report> report(HttpServletRequest r, @PathVariable Long id, @PathVariable Long reportId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.report(Identity.user(r).id, id, reportId));
  }
}
