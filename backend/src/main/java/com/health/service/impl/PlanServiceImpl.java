package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.*;
import com.health.mapper.PlanMapper;
import com.health.model.Models.*;
import com.health.service.PlanService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanServiceImpl implements PlanService {
  private final PlanMapper mapper;
  private final ObjectMapper json;

  public PlanServiceImpl(PlanMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  public List<Plan> list(Long uid) {
    return mapper.list(uid);
  }

  public List<AgentPlan> agentPlans(Long uid) {
    return mapper.agentPlans(uid);
  }

  public Plan get(Long uid, Long id) {
    Plan p = mapper.get(uid, id);
    if (p == null) throw ApiException.missing();
    p.items = mapper.items(id);
    return p;
  }

  @Transactional
  public Plan status(Long uid, Long id, String status) {
    if (mapper.lock(uid, id) == null) throw ApiException.missing();
    mapper.status(id, status);
    return get(uid, id);
  }

  @Transactional
  public Map<String, Object> checkin(Long uid, Long id, LocalDate date) {
    PlanItem item = mapper.itemOwner(uid, id);
    if (item == null) throw ApiException.missing();
    Plan p = mapper.lock(uid, item.planId);
    if (!date.equals(item.date)) throw new ApiException(400, "DATE_MISMATCH", "打卡日期须与计划项目日期一致");
    if (!"ACTIVE".equals(p.status)) throw ApiException.conflict("仅进行中的计划可打卡");
    Map<String, Object> existing = mapper.checkin(id);
    if (existing != null) return existing;
    mapper.insertCheckin(id, date);
    return mapper.checkin(id);
  }

  @Transactional
  public void uncheck(Long uid, Long id) {
    PlanItem item = mapper.itemOwner(uid, id);
    if (item == null) throw ApiException.missing();
    mapper.lock(uid, item.planId);
    mapper.deleteCheckin(id);
  }

  public List<Report> reports(Long uid) {
    return mapper.reports(uid);
  }

  public Report report(Long uid, Long id) {
    Report r = mapper.report(uid, id);
    if (r == null) throw ApiException.missing();
    try {
      r.statistics = r.statisticsJson == null ? null : json.readTree(r.statisticsJson);
      r.references = r.referencesJson == null ? null : json.readTree(r.referencesJson);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("已保存报告JSON无效", e);
    }
    return r;
  }
}
