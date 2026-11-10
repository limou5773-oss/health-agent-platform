package com.health.api;

import com.health.model.Models.*;
import com.health.security.Identity;
import com.health.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/tasks")
public class TaskController {
  private final TaskService service;

  public TaskController(TaskService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Task create(HttpServletRequest r, @Valid @RequestBody Requests.TaskCreate q) {
    return service.create(Identity.user(r).id, q);
  }

  @GetMapping
  public List<Task> list(HttpServletRequest r) {
    return service.list(Identity.user(r).id);
  }

  @GetMapping("/{id}")
  public Task get(HttpServletRequest r, @PathVariable String id) {
    return service.get(Identity.user(r).id, id);
  }

  @PostMapping("/{id}/input")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Task input(
      HttpServletRequest r, @PathVariable String id, @Valid @RequestBody Requests.TaskInput q) {
    return service.input(Identity.user(r).id, id, q);
  }

  @PutMapping("/{id}/draft")
  public Task draft(
      HttpServletRequest r, @PathVariable String id, @Valid @RequestBody Requests.Draft q) {
    return service.draft(Identity.user(r).id, id, q);
  }

  @PostMapping("/{id}/confirm")
  public Map<String, Object> confirm(
      HttpServletRequest r, @PathVariable String id, @Valid @RequestBody Requests.Confirm q) {
    return service.confirm(Identity.user(r).id, id, q);
  }

  @PostMapping("/{id}/cancel")
  public Task cancel(HttpServletRequest r, @PathVariable String id) {
    return service.cancel(Identity.user(r).id, id);
  }

  @PostMapping("/{id}/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Task retry(HttpServletRequest r, @PathVariable String id) {
    return service.retry(Identity.user(r).id, id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest r, @PathVariable String id) {
    service.delete(Identity.user(r).id, id);
  }
}
