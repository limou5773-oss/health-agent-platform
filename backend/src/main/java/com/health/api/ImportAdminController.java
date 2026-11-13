package com.health.api;

import com.health.model.ImportAdminModels.*;
import com.health.model.Models.Page;
import com.health.service.ImportAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ImportAdminController {
  private final ImportAdminService service;

  public ImportAdminController(ImportAdminService service) { this.service = service; }

  @GetMapping("/admin/imports")
  public Page<Batch> batches(@RequestParam Kind kind, @RequestParam(defaultValue = "false") boolean issuesOnly,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    return service.batches(kind, issuesOnly, page, pageSize);
  }

  @GetMapping("/admin/imports/{kind}/{id}/issues")
  public List<Issue> issues(@PathVariable Kind kind, @PathVariable Long id) {
    return service.issues(kind, id);
  }

  @GetMapping("/import-templates/{kind}")
  public Template template(@PathVariable Kind kind) { return service.template(kind); }

  @PutMapping("/admin/import-templates/{kind}")
  public Template save(@PathVariable Kind kind, @Valid @RequestBody Save body) {
    return service.saveTemplate(kind, body.instructions());
  }

  public record Save(@NotNull @Size(max = 1000) String instructions) {}
}
