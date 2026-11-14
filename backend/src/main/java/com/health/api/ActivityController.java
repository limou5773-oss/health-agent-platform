package com.health.api;

import com.health.model.ImportAdminModels.Kind;
import com.health.service.ImportAdminService;
import com.health.model.ActivityModels.*;
import com.health.model.Models.Page;
import com.health.security.Identity;
import com.health.service.ActivityService;
import com.health.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {
  private final ActivityService service;
  private final NotificationService notifications;
  private final ImportAdminService importAdmin;

  public ActivityController(ActivityService service, NotificationService notifications, ImportAdminService importAdmin) {
    this.service = service;
    this.notifications = notifications;
    this.importAdmin = importAdmin;
  }

  @GetMapping("/steps")
  public Page<StepRecord> steps(HttpServletRequest r, @Valid @ModelAttribute Requests.SleepQuery q) {
    return service.steps(Identity.user(r).id, q);
  }

  @PostMapping("/steps")
  @ResponseStatus(HttpStatus.CREATED)
  public StepRecord createStep(HttpServletRequest r, @Valid @RequestBody ActivityRequests.Step q) {
    return service.createStep(Identity.user(r).id, q);
  }

  @PutMapping("/steps/{id}")
  public StepRecord updateStep(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody ActivityRequests.Step q) {
    return service.updateStep(Identity.user(r).id, id, q);
  }

  @DeleteMapping("/steps/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteStep(HttpServletRequest r, @PathVariable Long id) { service.deleteStep(Identity.user(r).id, id); }

  @GetMapping("/steps/sources")
  public List<String> stepSources(HttpServletRequest r) { return service.stepSources(Identity.user(r).id); }

  @GetMapping("/steps/statistics")
  public StepStatistics stepStatistics(HttpServletRequest r, @Valid @ModelAttribute Requests.StatisticsQuery q) {
    return service.stepStatistics(Identity.user(r).id, q.startDate, q.endDate, q.sourceName);
  }

  @GetMapping("/goal")
  public Goal goal(HttpServletRequest r) { return service.goal(Identity.user(r).id); }

  @PutMapping("/goal")
  public Goal updateGoal(HttpServletRequest r, @Valid @RequestBody ActivityRequests.Goal q) {
    return service.updateGoal(Identity.user(r).id, q);
  }

  @PostMapping(value = "/step-imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public StepImportBatch preview(HttpServletRequest r, @RequestPart MultipartFile file,
      @RequestParam(defaultValue = "CSV") StepImportFormat format, @RequestParam(required = false) String timezone) {
    Long userId = Identity.user(r).id;
    try {
      return format == StepImportFormat.APPLE_HEALTH
          ? service.previewApple(userId, file) : service.preview(userId, file, timezone);
    } catch (ApiException e) {
      if (e.status == 400) {
        importAdmin.rejected(Kind.STEPS, userId, e.code);
        notifications.importRejected(userId, true);
      }
      throw e;
    }
  }

  @PostMapping("/step-imports/{id}/confirm")
  public StepImportBatch confirm(HttpServletRequest r, @PathVariable Long id) { return service.confirm(Identity.user(r).id, id); }

  @GetMapping("/step-imports")
  public List<StepImportBatch> imports(HttpServletRequest r) { return service.imports(Identity.user(r).id); }

  @GetMapping("/workouts")
  public Page<Workout> workouts(HttpServletRequest r, @Valid @ModelAttribute Requests.SleepQuery q) {
    return service.workouts(Identity.user(r).id, q);
  }

  @PostMapping("/workouts")
  @ResponseStatus(HttpStatus.CREATED)
  public Workout createWorkout(HttpServletRequest r, @Valid @RequestBody ActivityRequests.Workout q) {
    return service.createWorkout(Identity.user(r).id, q);
  }

  @PutMapping("/workouts/{id}")
  public Workout updateWorkout(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody ActivityRequests.Workout q) {
    return service.updateWorkout(Identity.user(r).id, id, q);
  }

  @DeleteMapping("/workouts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWorkout(HttpServletRequest r, @PathVariable Long id) { service.deleteWorkout(Identity.user(r).id, id); }

  @GetMapping("/workouts/sources")
  public List<String> workoutSources(HttpServletRequest r) { return service.workoutSources(Identity.user(r).id); }

  @GetMapping("/workouts/statistics")
  public WorkoutStatistics workoutStatistics(HttpServletRequest r, @Valid @ModelAttribute Requests.StatisticsQuery q) {
    return service.workoutStatistics(Identity.user(r).id, q.startDate, q.endDate, q.sourceName);
  }

  @GetMapping("/plan-items")
  public List<EligiblePlanItem> planItems(HttpServletRequest r, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service.planItems(Identity.user(r).id, date);
  }
}
