package com.health.service.impl;

import com.health.security.Identity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.*;
import com.health.mapper.PlanMapper;
import com.health.mapper.TaskMapper;
import com.health.model.Models.*;
import com.health.service.AccountService;
import com.health.service.PlanService;
import com.health.service.TaskService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanServiceImpl implements PlanService {
  private final PlanMapper mapper;
  private final TaskMapper tasks;
  private final ObjectMapper json;
  private final AccountService accounts;
  private final TaskService agentTasks;

  public PlanServiceImpl(PlanMapper mapper, TaskMapper tasks, ObjectMapper json, AccountService accounts,
      TaskService agentTasks) {
    this.mapper = mapper;
    this.tasks = tasks;
    this.json = json;
    this.accounts = accounts;
    this.agentTasks = agentTasks;
  }

  public List<Plan> list(Long uid) {
    return mapper.list(uid);
  }

  public List<AgentPlan> agentPlans(Long uid) {
    return mapper.agentPlans(uid);
  }

  @Transactional(readOnly = true)
  public Plan get(Long uid, Long id) {
    Plan p = mapper.get(uid, id);
    if (p == null) throw ApiException.missing();
    p.items = mapper.items(id);
    p.progress = progress(p, today(uid));
    return p;
  }

  @Transactional
  public Plan status(Long uid, Long id, String status) {
    Identity.requireExisting(tasks.lockUser(uid));
    Plan plan = mapper.lock(uid, id);
    if (plan == null) throw ApiException.missing();
    if (!status.equals(plan.status)) {
      mapper.status(id, status);
      mapper.insertStatusChange(id, plan.status, status);
    }
    return get(uid, id);
  }

  @Transactional
  public Map<String, Object> checkin(Long uid, Long id, LocalDate date) {
    Identity.requireExisting(tasks.lockUser(uid));
    PlanItem item = mapper.itemOwner(uid, id);
    if (item == null) throw ApiException.missing();
    Plan p = mapper.lock(uid, item.planId);
    if (mapper.currentItem(id) == null) throw ApiException.conflict("该任务已被计划修订替换");
    if (!date.equals(item.date)) throw new ApiException(400, "DATE_MISMATCH", "打卡日期须与计划项目日期一致");
    if (!"ACTIVE".equals(p.status)) throw ApiException.conflict("仅进行中的计划可打卡");
    Map<String, Object> existing = mapper.checkin(id);
    if (existing != null) return existing;
    if (date.isAfter(today(uid)))
      throw new ApiException(400, "CHECKIN_FUTURE_DATE", "尚未到该任务的打卡日期");
    mapper.insertCheckin(id, date);
    mapper.saveMissedReason(id, "");
    return mapper.checkin(id);
  }

  @Transactional
  public void uncheck(Long uid, Long id) {
    Identity.requireExisting(tasks.lockUser(uid));
    PlanItem item = mapper.itemOwner(uid, id);
    if (item == null) throw ApiException.missing();
    mapper.lock(uid, item.planId);
    if (mapper.deleteCheckin(id) > 0) {
      agentTasks.invalidateCheckins(uid, item.date);
      mapper.invalidateHealthReports(uid, item.date, item.date, ReportSourceChange.CHECKIN_REVOKED);
    }
  }

  public List<Report> reports(Long uid) {
    return mapper.reports(uid);
  }

  private LocalDate today(Long uid) {
    return LocalDate.now(ZoneId.of(accounts.profile(uid).timezone));
  }

  private static PlanProgress progress(Plan plan, LocalDate today) {
    Map<LocalDate, Boolean> days = new TreeMap<>();
    int due = 0;
    int completed = 0;
    for (PlanItem item : plan.items) {
      if (item.date.isAfter(today)) continue;
      due++;
      if (item.completed) completed++;
      days.merge(item.date, item.completed, (all, next) -> all && next);
    }
    int streak = 0;
    int longest = 0;
    LocalDate previous = null;
    for (var day : days.entrySet()) {
      LocalDate date = day.getKey();
      if (day.getValue()) {
        streak = previous != null && previous.plusDays(1).equals(date) ? streak + 1 : 1;
        longest = Math.max(longest, streak);
      } else if (!date.equals(today)) {
        streak = 0;
      } else {
        break;
      }
      previous = date;
    }
    LocalDate cutoff = Boolean.TRUE.equals(days.get(today)) ? today : today.minusDays(1);
    if (plan.endDate.isBefore(cutoff)) cutoff = plan.endDate;
    int current = cutoff.equals(previous) ? streak : 0;
    BigDecimal rate = due == 0 ? null : BigDecimal.valueOf(completed * 100L)
        .divide(BigDecimal.valueOf(due), 2, RoundingMode.HALF_UP);
    return new PlanProgress(today, due, completed, rate, current, longest);
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
