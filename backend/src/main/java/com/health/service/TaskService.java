package com.health.service;

import com.health.api.Requests;
import com.health.model.Models.*;
import com.health.model.TaskAudit;
import com.health.model.PlanTemplateModels.Template;
import java.time.LocalDate;
import java.util.*;

public interface TaskService {
  Task create(Long uid, Requests.TaskCreate r);
  Task createTemplateDraft(Long uid, Template template, Requests.Draft draft);

  List<Task> list(Long uid);

  Task get(Long uid, String id);

  Task input(Long uid, String id, Requests.TaskInput r);

  Task draft(Long uid, String id, Requests.Draft r);

  Map<String, Object> confirm(Long uid, String id, Requests.Confirm r);

  Task cancel(Long uid, String id);

  Task retry(Long uid, String id);

  void delete(Long uid, String id);

  List<Map<String, Object>> admin();
  TaskAudit audit(String id);
  void feedback(Long uid, String id, TaskAudit.Feedback feedback);

  Task internalIdentity(String id, String token);

  Map<String, Object> context(Task task, Profile profile);

  Long step(Task task, Requests.Step r);

  void invalidate(Long uid, LocalDate date, String source);

  void invalidateDiary(Long uid, Set<LocalDate> dates);
  void invalidateCombined(Long uid, LocalDate start, LocalDate end);

  void recoverInterrupted();
}
