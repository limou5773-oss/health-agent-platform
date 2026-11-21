package com.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.ApiException;
import com.health.api.PlanRevisionRequests.Content;
import com.health.api.PlanRevisionRequests.Item;
import com.health.api.PlanRevisionRequests.Save;
import com.health.api.PlanRevisionRequests.Suggest;
import com.health.model.PlanRevisionModels.Revision;
import com.health.service.AccountService;
import com.health.service.PlanRevisionAgentService;
import com.health.service.PlanRevisionService;
import com.health.service.PlanRevisionService.ReviewBasis;
import com.health.service.PlanReviewService;
import com.health.service.PlanService;
import com.health.service.SystemSettingsService;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class PlanRevisionAgentServiceImpl implements PlanRevisionAgentService {
  private final AccountService accounts;
  private final PlanService plans;
  private final PlanRevisionService revisions;
  private final PlanReviewService reviews;
  private final SystemSettingsService settings;
  private final Validator validator;
  private final ObjectMapper json;
  private final RestClient agent;

  public PlanRevisionAgentServiceImpl(AccountService accounts, PlanService plans, PlanRevisionService revisions,
      PlanReviewService reviews, SystemSettingsService settings, Validator validator, ObjectMapper json,
      @Value("${app.agent-url}") String url,
      @Value("${app.internal-service-key}") String key) {
    this.accounts = accounts;
    this.plans = plans;
    this.revisions = revisions;
    this.reviews = reviews;
    this.settings = settings;
    this.validator = validator;
    this.json = json;
    var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(95));
    agent = RestClient.builder().baseUrl(url).defaultHeader("X-Service-Key", key).requestFactory(factory).build();
  }

  @Override
  public Revision suggest(Long userId, Long planId, Suggest request) {
    var profile = accounts.profile(userId);
    if (!profile.aiConsent) throw new ApiException(403, "AI_CONSENT_REQUIRED", "请先在个人档案中授权AI分析");
    var snapshot = revisions.revisions(userId, planId);
    var plan = plans.get(userId, planId);
    if (snapshot.latestVersion() != request.expectedVersion() || snapshot.activeVersion() != plan.version)
      throw new ApiException(409, "STALE_PLAN_REVISION", "草稿版本已过期，请刷新计划后重试");
    Content base = snapshot.draft() != null ? snapshot.draft().content : new Content(plan.title, plan.reason,
        plan.startDate, plan.endDate, plan.items.stream().map(i ->
            new Item(i.id, i.date, i.time, i.title, i.description, i.reminderTime)).toList());
    ReviewBasis reviewBasis = null;
    if (request.review() != null) {
      var query = request.review();
      boolean allowed = switch (query.metric()) {
        case SLEEP -> profile.aiSleep;
        case STEPS, WORKOUT -> profile.aiActivity;
        case WEIGHT -> profile.aiWeight;
        case HEART_RATE -> profile.aiHeartRate;
        case MEAL, WATER -> false;
      };
      if (!allowed) throw new ApiException(403, "AI_METRIC_NOT_ALLOWED", "该复盘指标未开放或未授权给 AI");
      var review = reviews.review(userId, planId, query);
      if (review.planVersion() != plan.version)
        throw new ApiException(409, "STALE_PLAN_REVISION", "计划版本已变化，请重新复盘");
      if (review.scheduled() == 0 && review.comparison().recent().validDays() == 0
          && review.comparison().historical().validDays() == 0)
        throw new ApiException(422, "REVIEW_NO_DATA", "所选周期暂无计划任务或可用指标记录");
      reviewBasis = new ReviewBasis(query, review.evidence());
    }
    var config = settings.get();
    var payload = new LinkedHashMap<String, Object>();
    payload.put("instruction", request.instruction());
    payload.put("plan", base);
    payload.put("completedIds", plan.items.stream().filter(i -> i.completed).map(i -> i.id).toList());
    payload.put("modelName", config.modelName());
    payload.put("timeoutSeconds", config.modelTimeoutSeconds());
    if (reviewBasis != null) payload.put("review", reviewBasis.evidence());
    Suggestion suggestion;
    try {
      suggestion = agent.post().uri("/internal/model/plan-revision")
          .body(json.valueToTree(payload))
          .retrieve().body(Suggestion.class);
    } catch (ResourceAccessException e) {
      throw new ApiException(502, "AGENT_UNAVAILABLE", "无法连接 Agent 服务或修订请求超时");
    } catch (RestClientException e) {
      throw new ApiException(502, "AGENT_REVISION_FAILED", "Agent 未返回可用的修订，请检查模型配置或调整要求");
    }
    if (suggestion == null) throw new ApiException(502, "INVALID_AGENT_RESULT", "Agent 未返回修订草稿");
    Save draft = new Save(request.expectedVersion(), suggestion.note(), suggestion.content());
    if (!validator.validate(draft).isEmpty())
      throw new ApiException(502, "INVALID_AGENT_RESULT", "Agent 草稿字段超出计划约束，请调整要求");
    return revisions.saveSuggestion(userId, planId, draft, profile.aiConsentVersion,
        plan.version, snapshot.draft() != null, reviewBasis);
  }

  private record Suggestion(String note, Content content) {}
}
