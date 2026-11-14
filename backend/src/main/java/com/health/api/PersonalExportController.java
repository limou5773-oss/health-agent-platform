package com.health.api;

import com.health.security.Identity;
import com.health.service.PersonalExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class PersonalExportController {
  private final PersonalExportService service;

  public PersonalExportController(PersonalExportService service) {
    this.service = service;
  }

  @GetMapping("/api/profile/export")
  public ResponseEntity<StreamingResponseBody> export(HttpServletRequest request) {
    Long userId = Identity.user(request).id;
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"personal-health-data.json\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(output -> service.export(userId, output));
  }
}
