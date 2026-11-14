package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.ApiException;
import com.health.api.Requests.StatisticsQuery;
import com.health.mapper.AccountMapper;
import com.health.mapper.FamilyMapper;
import com.health.model.BaselineModels.Metric;
import com.health.model.BaselineModels.Period;
import com.health.model.FamilyModels.Grant;
import com.health.model.FamilyModels.Invitation;
import com.health.model.Models.Report;
import com.health.security.Identity;
import com.health.service.BaselineService;
import com.health.service.FamilyService;
import com.health.service.PlanService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyServiceImpl implements FamilyService {
  private final AccountMapper accounts;
  private final FamilyMapper mapper;
  private final BaselineService baseline;
  private final PlanService plans;
  private final ObjectMapper json;

  public FamilyServiceImpl(AccountMapper accounts, FamilyMapper mapper, BaselineService baseline,
      PlanService plans, ObjectMapper json) {
    this.accounts = accounts;
    this.mapper = mapper;
    this.baseline = baseline;
    this.plans = plans;
    this.json = json;
  }

  public List<Grant> list(Long userId) {
    return mapper.list(userId).stream().map(this::hydrate).toList();
  }

  @Transactional
  public void invite(Long userId, Invitation request) {
    var member = accounts.byUsername(request.username().strip());
    if (member == null || !member.enabled || userId.equals(member.id))
      throw new ApiException(400, "INVALID_FAMILY_MEMBER", "请选择另一位已启用的平台注册用户");
    long first = Math.min(userId, member.id), second = Math.max(userId, member.id);
    Identity.requireExisting(accounts.lockUser(first));
    Identity.requireExisting(accounts.lockUser(second));
    for (Long id : request.reportIds()) {
      Report report = plans.report(userId, id);
      if (report.sourceDeleted) throw ApiException.conflict("所选报告已失效");
    }
    Grant grant = new Grant();
    grant.ownerId = userId;
    grant.memberId = member.id;
    grant.metricsJson = json.valueToTree(request.metrics()).toString();
    grant.reportIdsJson = json.valueToTree(request.reportIds()).toString();
    mapper.removePair(userId, member.id);
    mapper.invite(grant, request.days());
  }

  public void accept(Long userId, Long id) {
    if (mapper.accept(userId, id) == 0) throw ApiException.conflict("邀请已处理、已失效或不存在");
  }

  public void revoke(Long userId, Long id) {
    if (mapper.revoke(userId, id) == 0) throw ApiException.missing();
  }

  @Transactional(readOnly = true)
  public Period statistics(Long userId, Long id, Metric metric, StatisticsQuery query) {
    Grant grant = readable(userId, id);
    if (!grant.metrics.contains(metric)) throw ApiException.missing();
    return baseline.summarize(grant.ownerId, metric, query.sourceName, query.startDate, query.endDate);
  }

  @Transactional(readOnly = true)
  public Report report(Long userId, Long id, Long reportId) {
    Grant grant = readable(userId, id);
    if (!grant.reportIds.contains(reportId)) throw ApiException.missing();
    Report report = plans.report(grant.ownerId, reportId);
    if (report.sourceDeleted) throw ApiException.missing();
    report.taskId = null;
    return report;
  }

  private Grant readable(Long userId, Long id) {
    Grant grant = mapper.readable(userId, id);
    if (grant == null) throw ApiException.missing();
    return hydrate(grant);
  }

  private Grant hydrate(Grant grant) {
    try {
      grant.metrics = json.readValue(grant.metricsJson, new TypeReference<>() {});
      grant.reportIds = json.readValue(grant.reportIdsJson, new TypeReference<>() {});
      return grant;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("家庭授权范围无法读取", e);
    }
  }
}
