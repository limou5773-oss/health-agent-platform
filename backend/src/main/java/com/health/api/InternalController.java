package com.health.api;

import com.health.model.Models.*;
import com.health.model.DiaryModels.DiaryContext;
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
  private final DiaryService diary;

  public InternalController(
      TaskService tasks, SleepService sleep, KnowledgeService knowledge, PlanService plans, DiaryService diary) {
    this.tasks = tasks;
    this.sleep = sleep;
    this.knowledge = knowledge;
    this.plans = plans;
    this.diary = diary;
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

  @GetMapping("/tools/diary")
  public DiaryContext diary(HttpServletRequest r) {
    Task t = (Task) r.getAttribute(TASK);
    if (!t.includeDiary) throw new ApiException(403, "DIARY_NOT_ENABLED", "本任务未启用日记背景");
    return diary.authorized(t.userId, t.startDate, t.endDate);
  }

  @GetMapping("/tools/plans")
  public List<AgentPlan> plans(HttpServletRequest r) {
    return plans.agentPlans(Identity.user(r).id);
  }

  @PostMapping("/steps")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Long> step(HttpServletRequest r, @Valid @RequestBody Requests.Step q) {
    return Map.of("id", tasks.step((Task) r.getAttribute(TASK), q));
  }
}
