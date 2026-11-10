package com.health.api;

import com.health.model.Models.Page;
import com.health.model.WeightModels.*;
import com.health.security.Identity;
import com.health.service.WeightService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weight")
public class WeightController {
  private final WeightService service;

  public WeightController(WeightService service) { this.service = service; }

  @GetMapping("/records")
  public Page<WeightRecord> records(HttpServletRequest r, @Valid @ModelAttribute Requests.SleepQuery q) {
    return service.records(Identity.user(r).id, q);
  }

  @GetMapping("/records/{id}")
  public WeightRecord record(HttpServletRequest r, @PathVariable Long id) {
    return service.record(Identity.user(r).id, id);
  }

  @PostMapping("/records")
  @ResponseStatus(HttpStatus.CREATED)
  public WeightRecord createRecord(HttpServletRequest r, @Valid @RequestBody WeightRequests.Measurement q) {
    return service.createRecord(Identity.user(r).id, q);
  }

  @PutMapping("/records/{id}")
  public WeightRecord updateRecord(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody WeightRequests.Measurement q) {
    return service.updateRecord(Identity.user(r).id, id, q);
  }

  @DeleteMapping("/records/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRecord(HttpServletRequest r, @PathVariable Long id) { service.deleteRecord(Identity.user(r).id, id); }

  @GetMapping("/sources")
  public List<String> sources(HttpServletRequest r) { return service.sources(Identity.user(r).id); }

  @GetMapping("/statistics")
  public WeightStatistics statistics(HttpServletRequest r, @Valid @ModelAttribute Requests.StatisticsQuery q) {
    return service.statistics(Identity.user(r).id, q.startDate, q.endDate, q.sourceName);
  }

  @GetMapping("/goal")
  public ResponseEntity<WeightGoal> goal(HttpServletRequest r) {
    WeightGoal goal = service.goal(Identity.user(r).id);
    return goal == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(goal);
  }

  @PutMapping("/goal")
  public WeightGoal saveGoal(HttpServletRequest r, @Valid @RequestBody WeightRequests.Goal q) {
    return service.saveGoal(Identity.user(r).id, q);
  }

  @DeleteMapping("/goal")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteGoal(HttpServletRequest r) { service.deleteGoal(Identity.user(r).id); }

  @GetMapping("/goal/progress")
  public ResponseEntity<WeightGoalProgress> goalProgress(HttpServletRequest r) {
    WeightGoalProgress progress = service.goalProgress(Identity.user(r).id);
    return progress == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(progress);
  }
}
