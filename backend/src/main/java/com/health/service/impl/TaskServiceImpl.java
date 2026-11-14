package com.health.service.impl;

import com.health.security.Identity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.health.api.*;
import com.health.mapper.*;
import com.health.model.Models.*;
import com.health.model.TaskAudit;
import com.health.model.PlanTemplateModels.Template;
import com.health.service.*;
import com.health.security.AgentPermissions;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.client.*;

@Service
public class TaskServiceImpl implements TaskService {
  private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);
  private static final String CLEARED_GOAL = "源记录变更，原任务内容已清除";
  private static final String SLEEP_INVALIDATED = "源睡眠记录已变更，相关分析已失效";
  private static final String DIARY_INVALIDATED = "源日记已变更或授权已撤销，相关分析已失效";
  private static final String COMBINED_INVALIDATED = "源健康记录已变更，综合分析已失效";
  private final TaskMapper mapper;
  private final PlanMapper plans;
  private final AccountService accounts;
  private final PreferenceService preferences;
  private final NotificationService notifications;
  private final SystemSettingsService systemSettings;
  private final ObjectMapper json;
  private final Validator validator;
  private final Executor executor;
  private final TransactionTemplate tx;
  private final RestClient agent;
  private final RestClient cleanup;

  public TaskServiceImpl(
      TaskMapper mapper,
      PlanMapper plans,
      AccountService accounts,
      PreferenceService preferences,
      NotificationService notifications,
      SystemSettingsService systemSettings,
      ObjectMapper json,
      Validator validator,
      Executor taskExecutor,
      PlatformTransactionManager manager,
      @Value("${app.agent-url}") String url,
      @Value("${app.internal-service-key}") String key) {
    this.mapper = mapper;
    this.plans = plans;
    this.accounts = accounts;
    this.preferences = preferences;
    this.notifications = notifications;
    this.systemSettings = systemSettings;
    this.json = json;
    this.validator = validator;
    this.executor = taskExecutor;
    this.tx = new TransactionTemplate(manager);
    if (key.isBlank()) throw new IllegalArgumentException("INTERNAL_SERVICE_KEY不能为空");
    var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(1200));
    var cleanupFactory = new JdkClientHttpRequestFactory(http);
    cleanupFactory.setReadTimeout(Duration.ofSeconds(10));
    var client = RestClient.builder().baseUrl(url).defaultHeader("X-Service-Key", key);
    this.agent = client.requestFactory(factory).build();
    this.cleanup = client.requestFactory(cleanupFactory).build();
  }

  @Transactional
  public Task create(Long uid, Requests.TaskCreate r) {
    Identity.requireExisting(mapper.lockUser(uid));
    Profile profile = accounts.profile(uid);
    AgentPermissions.require(profile, r.kind(), r.includeDiary());
    Task t = new Task();
    t.id = UUID.randomUUID().toString();
    t.userId = uid;
    t.kind = r.kind();
    t.goal = r.goal();
    t.status = "QUEUED";
    t.startDate = r.startDate();
    t.endDate = r.endDate();
    t.planStartDate = r.planStartDate();
    t.planEndDate = r.planEndDate();
    t.sourceName = profile.aiSleep ? r.sourceName() : null;
    t.includeDiary = r.includeDiary();
    t.aiConsentVersion = profile.aiConsentVersion;
    t.taskToken = newToken();
    mapper.insert(t);
    afterCommit(() -> run(t.id, "CREATE", null));
    return hydrate(mapper.get(t.id));
  }

  public List<Task> list(Long uid) {
    List<Task> result = mapper.list(uid);
    result.forEach(this::hydrate);
    return result;
  }

  @Transactional
  public Task createTemplateDraft(Long uid, Template template, Requests.Draft draft) {
    Identity.requireExisting(mapper.lockUser(uid));
    Task task = new Task();
    task.id = UUID.randomUUID().toString();
    task.userId = uid;
    task.kind = "PLAN";
    task.goal = template.title;
    task.status = "WAITING_CONFIRMATION";
    task.startDate = task.planStartDate = draft.startDate();
    task.endDate = task.planEndDate = draft.endDate();
    task.templateId = template.id;
    task.templateVersion = template.version;
    task.draftJson = json.valueToTree(draft).toString();
    task.taskToken = newToken();
    mapper.insert(task);
    return hydrate(mapper.get(task.id));
  }

  public Task get(Long uid, String id) {
    return hydrate(owned(mapper.get(id), uid));
  }

  @Transactional
  public Task input(Long uid, String id, Requests.TaskInput r) {
    Identity.requireExisting(mapper.lockUser(uid));
    Task t = owned(mapper.lock(id), uid);
    Profile profile = accounts.profile(uid);
    AgentPermissions.requireCurrent(profile, t);
    requireStatus(t, "WAITING_INPUT");
    t.pendingInput = r.input();
    if (profile.aiSleep && r.sourceName() != null && !r.sourceName().isBlank()) t.sourceName = r.sourceName();
    t.status = "QUEUED";
    t.question = null;
    t.error = null;
    mapper.update(t);
    afterCommit(() -> run(id, "INPUT", r.input()));
    return hydrate(t);
  }

  @Transactional
  public Task draft(Long uid, String id, Requests.Draft r) {
    Task t = owned(mapper.lock(id), uid);
    requireStatus(t, "WAITING_CONFIRMATION");
    int current = read(t.draftJson).path("version").asInt();
    if (current != r.version()) throw ApiException.conflict("草稿已更新，请刷新后重试");
    ObjectNode draft = json.valueToTree(r);
    draft.put("version", current + 1);
    t.draftJson = draft.toString();
    mapper.update(t);
    return hydrate(t);
  }

  @Transactional
  public Map<String, Object> confirm(Long uid, String id, Requests.Confirm r) {
    Identity.requireExisting(mapper.lockUser(uid));
    Task t = owned(mapper.lock(id), uid);
    if (t.planId != null) {
      if (!Objects.equals(t.confirmedVersion, r.version())) throw ApiException.conflict("草稿版本已失效");
      return Map.of("planId", t.planId, "taskId", id, "status", "COMPLETED");
    }
    requireStatus(t, "WAITING_CONFIRMATION");
    Requests.Draft d = convert(read(t.draftJson), Requests.Draft.class);
    if (d.version() != r.version()) throw ApiException.conflict("草稿版本已失效");
    Plan p = new Plan();
    p.userId = uid;
    p.taskId = id;
    p.title = d.title();
    p.reason = d.reason();
    p.startDate = d.startDate();
    p.endDate = d.endDate();
    plans.insert(p);
    for (Requests.DraftItem i : d.items()) {
      PlanItem item = new PlanItem();
      item.planId = p.id;
      item.date = i.date();
      item.time = i.time();
      item.title = i.title();
      item.description = i.description();
      plans.insertItem(item);
    }
    t.planId = p.id;
    t.confirmedVersion = d.version();
    t.confirmedAt = LocalDateTime.now();
    t.operationId = r.operationId();
    t.status = "COMPLETED";
    t.question = null;
    mapper.update(t);
    if (t.templateId == null) afterCommit(() -> closeConfirmation(id));
    return Map.of("planId", p.id, "taskId", id, "status", "COMPLETED");
  }

  @Transactional
  public Task cancel(Long uid, String id) {
    Task t = owned(mapper.lock(id), uid);
    if ("COMPLETED".equals(t.status)) throw ApiException.conflict("任务已完成");
    t.status = "CANCELLED";
    t.taskToken = newToken();
    t.question = null;
    t.draftJson = null;
    mapper.update(t);
    return hydrate(t);
  }

  @Transactional
  public Task retry(Long uid, String id) {
    Identity.requireExisting(mapper.lockUser(uid));
    Task t = owned(mapper.lock(id), uid);
    AgentPermissions.requireCurrent(accounts.profile(uid), t);
    requireStatus(t, "FAILED");
    if (!t.retryable) throw ApiException.conflict("该错误不能重试，请修正配置或参数后创建任务");
    t.status = "QUEUED";
    t.error = null;
    t.retryable = false;
    mapper.update(t);
    afterCommit(() -> run(id, "RETRY", null));
    return hydrate(t);
  }

  @Transactional
  public void delete(Long uid, String id) {
    Identity.requireExisting(mapper.lockUser(uid));
    Task task = owned(mapper.lock(id), uid);
    if (task.templateId == null) deleteCheckpoint(id);
    mapper.deleteSteps(id);
    mapper.detachPlans(id);
    mapper.detachReports(id);
    mapper.delete(id);
  }

  public List<Map<String, Object>> admin() {
    return mapper.admin();
  }

  @Transactional(readOnly = true)
  public TaskAudit audit(String id) {
    var metadata = mapper.audit(id);
    if (metadata == null) throw ApiException.missing();
    return new TaskAudit(metadata, mapper.auditSteps(id));
  }

  @Transactional
  public void feedback(Long uid, String id, TaskAudit.Feedback feedback) {
    Task task = owned(mapper.lock(id), uid);
    requireStatus(task, "COMPLETED");
    mapper.saveFeedback(id, feedback);
  }

  @Transactional
  public Task internalIdentity(String id, String token) {
    Task t = mapper.lock(id);
    if (t == null
        || token == null
        || !MessageDigest.isEqual(
            t.taskToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
      throw new ApiException(403, "INVALID_TASK_TOKEN", "任务认证无效");
    if (Set.of("CANCELLED", "FAILED").contains(t.status))
      throw new ApiException(403, "TASK_INACTIVE", "任务已终止");
    return t;
  }

  public Map<String, Object> context(Task t, Profile p) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", t.id);
    result.put("goal", t.goal);
    result.put("kind", t.kind);
    result.put("startDate", t.startDate);
    result.put("endDate", t.endDate);
    result.put("planStartDate", t.planStartDate);
    result.put("planEndDate", t.planEndDate);
    result.put("sourceName", t.sourceName);
    result.put("includeDiary", t.includeDiary);
    result.put("status", t.status);
    result.put("aiConsent", p.aiConsent);
    result.put("timezone", p.timezone);
    result.put("planId", t.planId);
    var runtime = systemSettings.get();
    result.put("modelName", runtime.modelName());
    result.put("modelTimeoutSeconds", runtime.modelTimeoutSeconds());
    if ("PLAN".equals(t.kind) && "RUNNING".equals(t.status)) {
      result.put("savedPreferences", preferences.list(t.userId));
    }
    return result;
  }

  @Transactional
  public Long step(Task t, Requests.Step r) {
    Task current = mapper.lock(t.id);
    if (current == null
        || !"RUNNING".equals(current.status)
        || !current.taskToken.equals(t.taskToken)) {
      throw ApiException.conflict("任务已结束或状态已变化，不能写入步骤");
    }
    Step s = new Step();
    s.taskId = t.id;
    s.name = r.name();
    s.status = r.status();
    s.inputSummary = r.inputSummary();
    s.outputSummary = r.outputSummary();
    mapper.insertStep(s);
    return s.id;
  }

  @Transactional
  public void invalidate(Long uid, LocalDate date, String source) {
    invalidateTasks(mapper.affected(uid, date, source), SLEEP_INVALIDATED);
    mapper.invalidateReports(uid, date, source);
  }

  @Transactional
  public void invalidateDiary(Long uid, Set<LocalDate> dates) {
    invalidateTasks(mapper.affectedDiary(uid, dates), DIARY_INVALIDATED);
    mapper.invalidateDiaryReports(uid, dates);
  }

  @Transactional
  public void invalidateCombined(Long uid, LocalDate start, LocalDate end) {
    invalidateTasks(mapper.affectedCombined(uid, start, end), COMBINED_INVALIDATED);
  }

  private void invalidateTasks(List<Task> tasks, String reason) {
    for (Task t : tasks) {
      if ("CANCELLED".equals(t.status) && CLEARED_GOAL.equals(t.goal)) continue;
      deleteCheckpoint(t.id);
      t.goal = CLEARED_GOAL;
      t.answer = null;
      t.question = null;
      t.statisticsJson = null;
      t.draftJson = null;
      t.pendingInput = null;
      t.feedback = null;
      t.error = reason;
      t.retryable = false;
      t.status = "CANCELLED";
      t.taskToken = newToken();
      mapper.update(t);
      mapper.deleteSteps(t.id);
    }
  }

  public void recoverInterrupted() {
    mapper.recoverInterrupted();
  }

  private void run(String id, String action, String input) {
    Task t = null;
    long startedAt = 0;
    try {
      t =
          tx.execute(
              status -> {
                Task task = mapper.lock(id);
                if (task == null || !"QUEUED".equals(task.status)) return null;
                accounts.identity(task.userId);
                AgentPermissions.requireCurrent(accounts.profile(task.userId), task);
                task.status = "RUNNING";
                mapper.update(task);
                return task;
              });
      if (t == null) return;
      startedAt = System.nanoTime();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("taskToken", t.taskToken);
      JsonNode result;
      if ("CREATE".equals(action)) {
        body.put("taskId", t.id);
        body.put("goal", t.goal);
        body.put("kind", t.kind);
        body.put("startDate", t.startDate);
        body.put("endDate", t.endDate);
        body.put("planStartDate", t.planStartDate);
        body.put("planEndDate", t.planEndDate);
        body.put("sourceName", t.sourceName);
        body.put("includeDiary", t.includeDiary);
        result = agent.post().uri("/internal/runs").body(body).retrieve().body(JsonNode.class);
      } else {
        body.put("action", action);
        if (input != null) body.put("input", input);
        result =
            agent
                .post()
                .uri("/internal/runs/{id}/resume", id)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
      }
      final Task started = t;
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      tx.executeWithoutResult(status -> {
        applyResult(started, result);
        mapper.addDuration(id, elapsedMs);
      });
    } catch (Exception e) {
      final Task started = t;
      long elapsedMs = started == null ? 0 : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      log.warn("Agent task {} failed ({})", id, e.getClass().getSimpleName());
      tx.executeWithoutResult(
          status -> {
            if (started != null) mapper.addDuration(id, elapsedMs);
            Task current = mapper.lock(id);
            if (current == null || !Set.of("QUEUED", "RUNNING").contains(current.status)) return;
            if (started != null && !current.taskToken.equals(started.taskToken)) return;
            current.status = "FAILED";
            current.retryable = retryable(e);
            current.error = failure(e);
            mapper.update(current);
          });
    }
  }

  private void applyResult(Task started, JsonNode result) {
    mapper.lockUser(started.userId);
    Task t = mapper.lock(started.id);
    if (t == null || !"RUNNING".equals(t.status) || !t.taskToken.equals(started.taskToken)) return;
    AgentPermissions.requireCurrent(accounts.profile(t.userId), t);
    if (result == null || !result.isObject()) throw invalidResult("Python未返回结构化结果");
    String status = result.path("status").asText();
    if (!Set.of("COMPLETED", "WAITING_INPUT", "WAITING_CONFIRMATION").contains(status))
      throw invalidResult("任务结果状态无效");
    t.status = status;
    t.answer = text(result, "answer");
    t.question = text(result, "question");
    t.statisticsJson = result.hasNonNull("statistics") ? result.get("statistics").toString() : null;
    if ("WAITING_INPUT".equals(status) && (t.question == null || t.question.isBlank()))
      throw invalidResult("待补充任务缺少问题");
    if ("WAITING_CONFIRMATION".equals(status)) {
      if (!"PLAN".equals(t.kind) || !result.path("draft").isObject())
        throw invalidResult("待确认任务缺少计划草稿");
      ObjectNode node = (ObjectNode) result.get("draft");
      node.put("version", 1);
      Requests.Draft d = convert(node, Requests.Draft.class);
      if (!validator.validate(d).isEmpty()
          || !d.startDate().equals(t.planStartDate)
          || !d.endDate().equals(t.planEndDate)) throw invalidResult("模型草稿未符合计划周期或字段约束");
      t.draftJson = node.toString();
    }
    if ("COMPLETED".equals(status)) {
      if ("PLAN".equals(t.kind)) throw invalidResult("计划必须经用户确认");
      if ("REPORT".equals(t.kind)) {
        JsonNode node = result.path("report");
        String title = text(node, "title"), content = text(node, "content");
        if (title == null
            || title.isBlank()
            || title.length() > 200
            || content == null
            || content.isBlank()
            || !node.path("references").isArray()) throw invalidResult("报告结果字段无效");
        Report report = new Report();
        report.userId = t.userId;
        report.taskId = t.id;
        report.title = title;
        report.content = content;
        report.startDate = t.startDate;
        report.endDate = t.endDate;
        report.sourceName = t.sourceName;
        report.includeDiary = t.includeDiary;
        report.statisticsJson = t.statisticsJson;
        report.referencesJson = node.get("references").toString();
        plans.insertReport(report);
        notifications.reportReady(t.userId, report.id);
        t.reportId = report.id;
      } else if (t.answer == null || t.answer.isBlank()) throw invalidResult("分析结果为空");
    }
    t.pendingInput = null;
    t.error = null;
    t.retryable = false;
    mapper.update(t);
  }

  private void closeConfirmation(String id) {
    Task t = mapper.get(id);
    if (t == null || !"COMPLETED".equals(t.status)) return;
    try {
      AgentPermissions.requireCurrent(accounts.profile(t.userId), t);
      agent
          .post()
          .uri("/internal/runs/{id}/resume", id)
          .body(Map.of("taskToken", t.taskToken, "action", "CONFIRM", "planId", t.planId))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      log.warn(
          "Plan saved, checkpoint closure failed for {} ({})", id, e.getClass().getSimpleName());
      tx.executeWithoutResult(
          status -> {
            Task current = mapper.lock(id);
            if (current != null && "COMPLETED".equals(current.status)) {
              current.error = "计划已保存，Python检查点闭合失败：" + failure(e);
              mapper.update(current);
            }
          });
    }
  }

  private void deleteCheckpoint(String id) {
    try {
      cleanup.delete().uri("/internal/runs/{id}", id).retrieve().toBodilessEntity();
    } catch (RestClientException e) {
      throw new ApiException(502, "CHECKPOINT_DELETE_FAILED", "Python检查点删除失败，请恢复分析服务后重试");
    }
  }

  private Task hydrate(Task t) {
    t.statistics = read(t.statisticsJson);
    t.draft = read(t.draftJson);
    t.steps = mapper.steps(t.id);
    return t;
  }

  private JsonNode read(String value) {
    if (value == null) return null;
    try {
      return json.readTree(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("已保存JSON无效", e);
    }
  }

  private <T> T convert(JsonNode value, Class<T> type) {
    try {
      return json.treeToValue(value, type);
    } catch (JsonProcessingException e) {
      throw invalidResult("Python返回字段格式无效");
    }
  }

  private static String text(JsonNode node, String key) {
    return node.hasNonNull(key) ? node.get(key).asText() : null;
  }

  private static Task owned(Task t, Long uid) {
    if (t == null || !t.userId.equals(uid)) throw ApiException.missing();
    return t;
  }

  private static void requireStatus(Task t, String status) {
    if (!status.equals(t.status)) throw ApiException.conflict("当前任务状态不允许此操作");
  }

  private void afterCommit(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            executor.execute(action);
          }
        });
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static ApiException invalidResult(String message) {
    return new ApiException(502, "INVALID_AGENT_RESULT", message);
  }

  private static boolean retryable(Exception e) {
    if (e instanceof ApiException a) return a.status >= 500;
    if (e instanceof RestClientResponseException h)
      return h.getStatusCode().value() >= 500
          || h.getStatusCode().value() == 408
          || h.getStatusCode().value() == 429;
    return e instanceof ResourceAccessException;
  }

  private String failure(Exception e) {
    if (e instanceof ApiException a) return a.code + "：" + a.getMessage();
    if (e instanceof RestClientResponseException h) {
      String malformed = "Python返回非规范错误响应 HTTP " + h.getStatusCode().value();
      try {
        JsonNode body = json.readTree(h.getResponseBodyAsString());
        if (body == null || !body.path("code").isTextual() || !body.path("message").isTextual())
          return malformed;
        String code = body.get("code").asText();
        String message = body.get("message").asText();
        if (!code.matches("[A-Z0-9_]{1,80}") || message.isBlank()) return malformed;
        String summary = "Python HTTP " + h.getStatusCode().value() + " " + code + "：" + message;
        return summary.length() > 850 ? summary.substring(0, 850) : summary;
      } catch (JsonProcessingException invalidJson) {
        return malformed;
      }
    }
    if (e instanceof ResourceAccessException) return "Python服务连接或请求超时";
    return "任务执行失败：" + e.getClass().getSimpleName();
  }
}
