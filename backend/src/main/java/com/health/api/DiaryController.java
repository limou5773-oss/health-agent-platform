package com.health.api;

import com.health.model.DiaryModels.DiaryRecord;
import com.health.model.Models.Page;
import com.health.security.Identity;
import com.health.service.DiaryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/diary")
public class DiaryController {
  private final DiaryService service;

  public DiaryController(DiaryService service) { this.service = service; }

  @GetMapping("/entries")
  public Page<DiaryRecord> entries(HttpServletRequest r, @Valid @ModelAttribute DiaryRequests.Query q) {
    return service.entries(Identity.user(r).id, q);
  }

  @GetMapping("/entries/{id}")
  public DiaryRecord entry(HttpServletRequest r, @PathVariable Long id) {
    return service.entry(Identity.user(r).id, id);
  }

  @PostMapping("/entries")
  @ResponseStatus(HttpStatus.CREATED)
  public DiaryRecord create(HttpServletRequest r, @Valid @RequestBody DiaryRequests.Entry q) {
    return service.create(Identity.user(r).id, q);
  }

  @PutMapping("/entries/{id}")
  public DiaryRecord update(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody DiaryRequests.Entry q) {
    return service.update(Identity.user(r).id, id, q);
  }

  @PatchMapping("/entries/{id}/agent-permission")
  public DiaryRecord permission(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody DiaryRequests.Permission q) {
    return service.permission(Identity.user(r).id, id, q.agentAllowed());
  }

  @DeleteMapping("/entries/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest r, @PathVariable Long id) {
    service.delete(Identity.user(r).id, id);
  }

  @GetMapping("/sources")
  public List<String> sources(HttpServletRequest r) { return service.sources(Identity.user(r).id); }
}
