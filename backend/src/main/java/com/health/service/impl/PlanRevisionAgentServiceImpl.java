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
import com.health.service.PlanService;
import com.health.service.SystemSettingsService;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Map;
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
  private final SystemSettingsService settings;
  private final Validator validator;
  private final ObjectMapper json;
  private final RestClient agent;

  public PlanRevisionAgentServiceImpl(AccountService accounts, PlanService plans, PlanRevisionService revisions,
      SystemSettingsService settings, Validator validator, ObjectMapper json, @Value("${app.agent-url}") String url,
      @Value("${app.internal-service-key}") String key) {
    this.accounts = accounts;
    this.plans = plans;
    this.revisions = revisions;
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
    var config = settings.get();
    Suggestion suggestion;
    try {
      suggestion = agent.post().uri("/internal/model/plan-revision")
          .body(json.valueToTree(Map.of("instruction", request.instruction(), "plan", base,
              "completedIds", plan.items.stream().filter(i -> i.completed).map(i -> i.id).toList(),
              "modelName", config.modelName(), "timeoutSeconds", config.modelTimeoutSeconds())))
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
        plan.version, snapshot.draft() != null);
  }

  private record Suggestion(String note, Content content) {}
}
