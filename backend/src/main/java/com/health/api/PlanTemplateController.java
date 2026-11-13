package com.health.api;

import com.health.api.PlanTemplateRequests.Save;
import com.health.api.PlanTemplateRequests.Use;
import com.health.model.Models.Task;
import com.health.model.PlanTemplateModels.Template;
import com.health.security.Identity;
import com.health.service.PlanTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PlanTemplateController {
  private final PlanTemplateService templates;

  public PlanTemplateController(PlanTemplateService templates) { this.templates = templates; }

  @GetMapping("/admin/plan-templates")
  public List<Template> admin() { return templates.list(true); }

  @PostMapping("/admin/plan-templates")
  @ResponseStatus(HttpStatus.CREATED)
  public Template create(@Valid @RequestBody Save request) { return templates.save(null, request); }

  @PutMapping("/admin/plan-templates/{id}")
  public Template update(@PathVariable Long id, @Valid @RequestBody Save request) {
    return templates.save(id, request);
  }

  @GetMapping("/plan-templates")
  public List<Template> active() { return templates.list(false); }

  @PostMapping("/plan-templates/{id}/draft")
  @ResponseStatus(HttpStatus.CREATED)
  public Task draft(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Use request) {
    return templates.draft(Identity.user(r).id, id, request);
  }
}
