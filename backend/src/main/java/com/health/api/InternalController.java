package com.health.api;

import com.health.model.Models.*;
import com.health.security.Identity;
import com.health.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/agent/tasks/{id}")
public class InternalController {
  public static final String TASK = "internalTask";
  public static final String PROFILE = "internalProfile";
  private final TaskService tasks;
  private final SleepService sleep;
  private final KnowledgeService knowledge;
  private final PlanService plans;

  public InternalController(
      TaskService tasks, SleepService sleep, KnowledgeService knowledge, PlanService plans) {
    this.tasks = tasks;
    this.sleep = sleep;
    this.knowledge = knowledge;
    this.plans = plans;
  }

  @GetMapping("/context")
  public Map<String, Object> context(HttpServletRequest r) {
    return tasks.context((Task) r.getAttribute(TASK), (Profile) r.getAttribute(PROFILE));
  }

  @GetMapping("/tools/profile")
  public Profile profile(HttpServletRequest r) {
    return (Profile) r.getAttribute(PROFILE);
  }

  @GetMapping("/tools/sleep-statistics")
  public Statistics statistics(HttpServletRequest r) {
    Task t = (Task) r.getAttribute(TASK);
    return sleep.statistics(t.userId, t.startDate, t.endDate, t.sourceName);
  }

  @GetMapping("/tools/knowledge")
  public List<Article> knowledge(@RequestParam(required = false) @Size(max = 200) String query) {
    return knowledge.list(query, false);
  }

  @GetMapping("/tools/plans")
  public List<Plan> plans(HttpServletRequest r) {
    return plans.list(Identity.user(r).id);
  }

  @PostMapping("/steps")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Long> step(HttpServletRequest r, @Valid @RequestBody Requests.Step q) {
    return Map.of("id", tasks.step((Task) r.getAttribute(TASK), q));
  }
}
