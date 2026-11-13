package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.ApiException;
import com.health.api.PlanRevisionRequests.*;
import com.health.mapper.PlanMapper;
import com.health.model.Models.Plan;
import com.health.model.Models.PlanItem;
import com.health.model.PlanRevisionModels.*;
import com.health.service.PlanRevisionService;
import com.health.service.PlanService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanRevisionServiceImpl implements PlanRevisionService {
  private final PlanMapper mapper;
  private final PlanService plans;
  private final ObjectMapper json;

  public PlanRevisionServiceImpl(PlanMapper mapper, PlanService plans, ObjectMapper json) {
    this.mapper = mapper;
    this.plans = plans;
    this.json = json;
  }

  @Override
  @Transactional(readOnly = true)
  public Revisions revisions(Long userId, Long planId) {
    Plan plan = plans.get(userId, planId);
    int latest = Math.max(plan.version, mapper.latestRevision(planId));
    List<Revision> rows = mapper.revisions(planId);
    Revision draft = rows.stream().filter(r -> "DRAFT".equals(r.status)).findFirst().orElse(null);
    List<Revision> history = rows.stream().filter(r -> "CONFIRMED".equals(r.status)).toList();
    for (Revision row : rows) read(row);
    return new Revisions(plan.version, latest, draft, history);
  }

  @Override
  @Transactional
  public Revision save(Long userId, Long planId, Save request) {
    Plan plan = lockPlan(userId, planId);
    int latest = Math.max(plan.version, mapper.latestRevision(planId));
    if (latest != request.expectedVersion()) throw stale();
    Map<Long, PlanItem> items = currentItems(planId);
    validateItems(items, request.content());
    if (latest == 1) {
      Content original = new Content(plan.title, plan.reason, plan.startDate, plan.endDate,
          items.values().stream().map(PlanRevisionServiceImpl::snapshot).toList());
      insert(planId, 1, "CONFIRMED", "初始计划", original, plan.createdAt);
    }
    mapper.discardDraft(planId);
    Revision draft = insert(planId, latest + 1, "DRAFT", request.note(), request.content(), null);
    return read(mapper.revision(planId, draft.version));
  }

  @Override
  @Transactional
  public Plan confirm(Long userId, Long planId, int version) {
    Plan plan = lockPlan(userId, planId);
    int latest = mapper.latestRevision(planId);
    if (version != latest) throw stale();
    if (plan.version == version && version > 1) return plans.get(userId, planId);
    Revision draft = mapper.revision(planId, version);
    if (draft == null || !"DRAFT".equals(draft.status)) throw stale();

    Content content = read(draft).content;
    Map<Long, PlanItem> items = currentItems(planId);
    validateItems(items, content);
    var kept = new HashSet<Long>();
    for (Item next : content.items()) {
      PlanItem current = items.get(next.id());
      if (current != null && snapshot(current).equals(next)) {
        kept.add(next.id());
      } else {
        PlanItem item = new PlanItem();
        item.planId = planId;
        item.date = next.date();
        item.time = next.time();
        item.title = next.title();
        item.description = next.description();
        item.reminderTime = next.reminderTime();
        mapper.insertItem(item);
      }
    }
    for (Long id : items.keySet()) {
      if (!kept.contains(id)) mapper.retireItem(id);
    }
    plan.title = content.title();
    plan.reason = content.reason();
    plan.startDate = content.startDate();
    plan.endDate = content.endDate();
    plan.version = version;
    mapper.applyRevision(plan);
    Content confirmed = new Content(plan.title, plan.reason, plan.startDate, plan.endDate,
        mapper.items(planId).stream().map(PlanRevisionServiceImpl::snapshot).toList());
    mapper.confirmRevision(planId, version, json.valueToTree(confirmed).toString());
    return plans.get(userId, planId);
  }

  @Override
  @Transactional
  public void discard(Long userId, Long planId, int version) {
    lockPlan(userId, planId);
    if (version != mapper.latestRevision(planId) || mapper.discardRevision(planId, version) == 0)
      throw stale();
  }

  private Plan lockPlan(Long userId, Long planId) {
    Plan plan = mapper.lock(userId, planId);
    if (plan == null) throw ApiException.missing();
    return plan;
  }

  private Map<Long, PlanItem> currentItems(Long planId) {
    Map<Long, PlanItem> items = new LinkedHashMap<>();
    for (PlanItem item : mapper.items(planId)) items.put(item.id, item);
    return items;
  }

  private static void validateItems(Map<Long, PlanItem> current, Content content) {
    var included = new HashSet<Long>();
    for (Item item : content.items()) {
      if (item.id() == null) continue;
      PlanItem previous = current.get(item.id());
      if (previous == null || !included.add(item.id()))
        throw new ApiException(400, "INVALID_PLAN_ITEM", "任务编号无效或重复");
      if (previous.completed && !snapshot(previous).equals(item))
        throw ApiException.conflict("已打卡任务须保留原内容，请刷新后调整草稿");
    }
    if (current.values().stream().anyMatch(item -> item.completed && !included.contains(item.id)))
      throw ApiException.conflict("已打卡任务不能移除，请刷新后调整草稿");
  }

  private Revision insert(Long planId, int version, String status, String note, Content content,
      LocalDateTime createdAt) {
    Revision revision = new Revision();
    revision.planId = planId;
    revision.version = version;
    revision.status = status;
    revision.note = note;
    revision.createdAt = createdAt;
    revision.contentJson = json.valueToTree(content).toString();
    mapper.insertRevision(revision);
    return revision;
  }

  private Revision read(Revision revision) {
    try {
      revision.content = json.readValue(revision.contentJson, Content.class);
      return revision;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("已保存的计划版本无法读取", e);
    }
  }

  private static Item snapshot(PlanItem item) {
    return new Item(item.id, item.date, item.time, item.title, item.description, item.reminderTime);
  }

  private static ApiException stale() {
    return new ApiException(409, "STALE_PLAN_REVISION", "草稿版本已过期，请刷新计划后重试");
  }
}
