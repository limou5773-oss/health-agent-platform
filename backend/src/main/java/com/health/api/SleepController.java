package com.health.api;

import com.health.model.Models.*;
import com.health.security.Identity;
import com.health.service.SleepService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class SleepController {
  private final SleepService service;

  public SleepController(SleepService service) {
    this.service = service;
  }

  @GetMapping("/sleep")
  public Page<SleepRecord> list(
      HttpServletRequest r, @Valid @ModelAttribute Requests.SleepQuery q) {
    return service.list(Identity.user(r).id, q);
  }

  @PostMapping("/sleep")
  @ResponseStatus(HttpStatus.CREATED)
  public SleepRecord create(HttpServletRequest r, @Valid @RequestBody Requests.Sleep q) {
    return service.create(Identity.user(r).id, q);
  }

  @PutMapping("/sleep/{id}")
  public SleepRecord update(
      HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Requests.Sleep q) {
    return service.update(Identity.user(r).id, id, q);
  }

  @DeleteMapping("/sleep/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest r, @PathVariable Long id) {
    service.delete(Identity.user(r).id, id);
  }

  @GetMapping("/sleep/sources")
  public List<String> sources(HttpServletRequest r) {
    return service.sources(Identity.user(r).id);
  }

  @GetMapping("/sleep/statistics")
  public Statistics stats(HttpServletRequest r, @Valid @ModelAttribute Requests.StatisticsQuery q) {
    return service.statistics(Identity.user(r).id, q.startDate, q.endDate, q.sourceName);
  }

  @GetMapping("/sleep/export")
  public ResponseEntity<byte[]> export(
      HttpServletRequest r, @Valid @ModelAttribute Requests.SleepQuery q) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sleep.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(service.export(Identity.user(r).id, q));
  }

  @PostMapping(value = "/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ImportBatch preview(
      HttpServletRequest r,
      @RequestPart MultipartFile file,
      @RequestParam(required = false) String timezone) {
    return service.preview(Identity.user(r).id, file, timezone);
  }

  @PostMapping("/imports/{id}/confirm")
  public ImportBatch confirm(HttpServletRequest r, @PathVariable Long id) {
    return service.confirm(Identity.user(r).id, id);
  }

  @PostMapping(value = "/imports/apple/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ImportBatch applePreview(HttpServletRequest r, @RequestPart MultipartFile file) {
    return service.applePreview(Identity.user(r).id, file);
  }

  @GetMapping("/imports")
  public List<ImportBatch> imports(HttpServletRequest r) {
    return service.imports(Identity.user(r).id);
  }
}
