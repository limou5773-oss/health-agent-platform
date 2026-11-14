package com.health.api;

import com.health.model.Models.*;
import com.health.model.DiaryModels.DiaryContext;
import com.health.model.CombinedAnalysis;
import com.health.model.HealthReportModels.CheckinSummary;
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
  private final CombinedAnalysisService combined;

  public InternalController(
      TaskService tasks, SleepService sleep, KnowledgeService knowledge, PlanService plans, DiaryService diary,
      CombinedAnalysisService combined) {
    this.tasks = tasks;
    this.sleep = sleep;
    this.knowledge = knowledge;
    this.plans = plans;
    this.diary = diary;
    this.combined = combined;
  }

  @GetMapping("/context")
  public Map<String, Object> context(HttpServletRequest r) {
    return tasks.context((Task) r.getAttribute(TASK), (Profile) r.getAttribute(PROFILE));
  }

  @GetMapping("/tools/profile")
  public Map<String, Object> profile(HttpServletRequest r) {
    Profile profile = (Profile) r.getAttribute(PROFILE);
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("heightCm", profile.heightCm);
    fields.put("timezone", profile.timezone);
    fields.put("goal", profile.goal);
    fields.put("preferences", profile.preferences);
    if (profile.aiWeight) fields.put("weightKg", profile.weightKg);
    return fields;
  }

  @GetMapping("/tools/sleep-statistics")
  public Statistics statistics(HttpServletRequest r) {
    Task t = (Task) r.getAttribute(TASK);
    if (!((Profile) r.getAttribute(PROFILE)).aiSleep)
      throw new ApiException(403, "AI_METRIC_NOT_ALLOWED", "未授权睡眠分析");
    return sleep.statistics(t.userId, t.startDate, t.endDate, t.sourceName);
  }

  @GetMapping("/tools/sleep-records")
  public AgentSleepRecords sleepRecords(HttpServletRequest r) {
    Task t = (Task) r.getAttribute(TASK);
    if ("COMBINED".equals(t.kind))
      throw new ApiException(403, "TOOL_NOT_ENABLED", "综合分析任务只读取已授权的逐日统计");
    var query = new Requests.SleepQuery();
    query.pageSize = 20;
    query.startDate = t.startDate;
    query.endDate = t.endDate;
    query.sourceName = t.sourceName;
    Page<SleepRecord> page = sleep.list(t.userId, query);
    return new AgentSleepRecords(page.total(), page.items().stream()
        .map(record -> new AgentSleepRecord(record.id, record.startTime, record.endTime,
            record.sleepDate, record.durationMinutes, record.sourceName, record.corrected))
        .toList());
  }

  @GetMapping("/tools/knowledge")
  public List<Article> knowledge(@RequestParam(required = false) @Size(max = 200) String query) {
    return knowledge.list(query, false);
  }

  @GetMapping("/tools/combined-statistics")
  public CombinedAnalysis combined(HttpServletRequest r) {
    Task task = (Task) r.getAttribute(TASK);
    if (!"COMBINED".equals(task.kind)) throw new ApiException(403, "TOOL_NOT_ENABLED", "本任务未启用综合分析");
    return combined.statistics(task, (Profile) r.getAttribute(PROFILE));
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

  @GetMapping("/tools/checkin-summary")
  public CheckinSummary checkinSummary(HttpServletRequest r) {
    Task t = (Task) r.getAttribute(TASK);
    var completion = plans.completion(t.userId, t.startDate, t.endDate);
    return new CheckinSummary(t.startDate, t.endDate, completion.scheduled(),
        completion.completed(), completion.rate());
  }

  @PostMapping("/steps")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Long> step(HttpServletRequest r, @Valid @RequestBody Requests.Step q) {
    return Map.of("id", tasks.step((Task) r.getAttribute(TASK), q));
  }
}
