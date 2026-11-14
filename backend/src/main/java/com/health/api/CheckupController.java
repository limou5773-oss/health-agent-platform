package com.health.api;

import com.health.model.CheckupModels.*;
import com.health.model.Models.Page;
import com.health.security.Identity;
import com.health.service.CheckupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/checkups")
public class CheckupController {
  private final CheckupService service;

  public CheckupController(CheckupService service) {
    this.service = service;
  }

  @PostMapping(value = "/preview", consumes = "multipart/form-data")
  public Preview preview(HttpServletRequest r, @RequestPart MultipartFile photo) {
    return service.preview(Identity.user(r).id, photo);
  }

  @GetMapping
  public Page<Report> list(HttpServletRequest r, @RequestParam(defaultValue = "1") @Min(1) int page) {
    return service.list(Identity.user(r).id, page);
  }

  @GetMapping("/{id}")
  public Report get(HttpServletRequest r, @PathVariable Long id) {
    return service.get(Identity.user(r).id, id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Report save(HttpServletRequest r, @Valid @RequestBody Draft draft) {
    return service.save(Identity.user(r).id, draft);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest r, @PathVariable Long id) {
    service.delete(Identity.user(r).id, id);
  }
}
