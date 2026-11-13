package com.health.api;

import com.health.model.Models.Page;
import com.health.model.TimelineModels.Event;
import com.health.model.TimelineModels.EventType;
import com.health.security.Identity;
import com.health.service.TimelineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline/events")
public class TimelineController {
  private final TimelineService service;

  public TimelineController(TimelineService service) {
    this.service = service;
  }

  @GetMapping
  public Page<Event> events(HttpServletRequest r, @Valid @ModelAttribute TimelineRequests.Query q) {
    return service.events(Identity.user(r).id, q);
  }

  @GetMapping("/{type}/{id}")
  public Event event(HttpServletRequest r, @PathVariable EventType type, @PathVariable Long id) {
    return service.event(Identity.user(r).id, type, id);
  }

  @GetMapping("/export")
  public ResponseEntity<StreamingResponseBody> export(HttpServletRequest r, @Valid @ModelAttribute TimelineRequests.Query q) {
    Long userId = Identity.user(r).id;
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"health-records.json\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(output -> service.exportEvents(userId, q, output));
  }
}
