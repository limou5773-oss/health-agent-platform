package com.health.service.impl;

import com.health.api.ApiException;
import com.health.api.PlanCoordinationRequest;
import com.health.api.PlanRevisionRequests.Content;
import com.health.api.PlanRevisionRequests.Item;
import com.health.mapper.PlanMapper;
import com.health.model.Models.PlanItem;
import com.health.service.AccountService;
import com.health.service.PlanCoordinationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanCoordinationServiceImpl implements PlanCoordinationService {
  private final PlanMapper plans;
  private final AccountService accounts;

  public PlanCoordinationServiceImpl(PlanMapper plans, AccountService accounts) {
    this.plans = plans;
    this.accounts = accounts;
  }

  @Override
  @Transactional(readOnly = true)
  public Preview preview(Long userId, Long planId, PlanCoordinationRequest request) {
    if (!request.availableFrom().isBefore(request.availableUntil()) || request.priorityPlanIds().contains(planId)
        || !request.availableFrom().equals(request.availableFrom().withSecond(0).withNano(0))
        || !request.availableUntil().equals(request.availableUntil().withSecond(0).withNano(0)))
      throw new ApiException(400, "INVALID_COORDINATION", "请选择不同的优先计划，并填写同日内精确到分钟的可用时段");
    var plan = plans.get(userId, planId);
    if (plan == null) throw ApiException.missing();
    if (!"ACTIVE".equals(plan.status)) throw ApiException.conflict("请选择进行中的计划");
    var now = LocalDateTime.now(ZoneId.of(accounts.profile(userId).timezone));

    List<PriorityItem> priority = new ArrayList<>();
    for (Long id : request.priorityPlanIds().stream().sorted().toList()) {
      var higher = plans.get(userId, id);
      if (higher == null) throw ApiException.missing();
      if (!"ACTIVE".equals(higher.status)) throw ApiException.conflict("优先计划须处于进行中");
      for (PlanItem item : plans.items(id)) priority.add(new PriorityItem(id, item));
    }

    var original = plans.items(planId);
    Set<LocalDateTime> occupied = new HashSet<>();
    for (PriorityItem entry : priority) occupied.add(at(entry.item));
    for (PlanItem item : original) occupied.add(at(item));
    List<Conflict> conflicts = new ArrayList<>();
    List<Item> draftItems = new ArrayList<>();
    int remaining = original.size();
    boolean changed = false;
    for (PlanItem item : original) {
      var scheduled = at(item);
      var duplicate = priority.stream().filter(entry -> entry.item.date.equals(item.date)
          && entry.item.title.strip().equalsIgnoreCase(item.title.strip())).findFirst();
      var conflict = duplicate.or(() -> priority.stream().filter(entry -> at(entry.item).equals(scheduled)).findFirst());
      if (conflict.isEmpty()) {
        draftItems.add(snapshot(item, item.time, item.reminderTime));
        continue;
      }

      String suggestion;
      if (item.completed || !item.date.atTime(item.time).isAfter(now)) {
        suggestion = "已打卡或已到执行时间，保留原安排";
        draftItems.add(snapshot(item, item.time, item.reminderTime));
      } else if (duplicate.isPresent() && remaining > 1) {
        suggestion = "移除此任务，保留优先计划的同日同名任务";
        remaining--;
        changed = true;
      } else if (duplicate.isPresent()) {
        suggestion = "这是计划中剩余的唯一任务，请手动处理";
        draftItems.add(snapshot(item, item.time, item.reminderTime));
      } else {
        LocalTime slot = availableTime(item.date, request, occupied, now);
        if (slot == null) {
          suggestion = "当天可用时段内无空闲分钟，请手动调整";
          draftItems.add(snapshot(item, item.time, item.reminderTime));
        } else {
          occupied.add(item.date.atTime(slot));
          LocalTime reminder = item.reminderTime == null ? null : slot;
          draftItems.add(snapshot(item, slot, reminder));
          suggestion = "改到 " + slot + (reminder == null ? "" : "，提醒随任务改到同一时刻");
          changed = true;
        }
      }
      var higher = conflict.orElseThrow();
      conflicts.add(new Conflict(item.id, item.title, item.date.atTime(item.time).toString(), higher.planId,
          higher.item.id, higher.item.title, higher.item.date.atTime(higher.item.time).toString(),
          duplicate.isPresent() ? "同日同名" : "同一分钟", suggestion));
    }
    Content draft = changed ? new Content(plan.title, plan.reason, plan.startDate, plan.endDate, draftItems) : null;
    return new Preview(plan.version, Math.max(plan.version, plans.latestRevision(plan.id)),
        conflicts, draft, "多目标协调：按优先计划与可用时段调整冲突任务");
  }

  private record PriorityItem(long planId, PlanItem item) {}

  private static LocalDateTime at(PlanItem item) {
    return item.date.atTime(item.time).withSecond(0).withNano(0);
  }

  private static LocalTime availableTime(LocalDate date, PlanCoordinationRequest request,
      Set<LocalDateTime> occupied, LocalDateTime now) {
    int startMinute = request.availableFrom().toSecondOfDay() / 60;
    int endMinute = request.availableUntil().toSecondOfDay() / 60;
    for (int minute = startMinute; minute < endMinute; minute++) {
      LocalTime slot = LocalTime.ofSecondOfDay(minute * 60L);
      LocalDateTime at = date.atTime(slot);
      if (at.isAfter(now) && !occupied.contains(at)) return slot;
    }
    return null;
  }

  private static Item snapshot(PlanItem item, LocalTime time, LocalTime reminder) {
    return new Item(item.id, item.date, time, item.title, item.description, reminder);
  }
}
