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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    Map<LocalDate, List<PriorityItem>> priorityByDate = new HashMap<>();
    List<Conflict> priorityConflicts = new ArrayList<>();
    Set<LocalDateTime> occupied = new HashSet<>();
    for (Long id : request.priorityPlanIds().stream().sorted().toList()) {
      var higher = plans.get(userId, id);
      if (higher == null) throw ApiException.missing();
      if (!"ACTIVE".equals(higher.status)) throw ApiException.conflict("优先计划须处于进行中");
      for (PlanItem item : plans.items(id)) {
        var sameDay = priorityByDate.computeIfAbsent(item.date, date -> new ArrayList<>());
        for (PriorityItem entry : sameDay) {
          if (entry.planId == id) continue;
          Match match = match(item, entry);
          if (match != null) priorityConflicts.add(conflict(id, item, match, "两份优先计划均保留，请检查安排"));
        }
        sameDay.add(new PriorityItem(id, item));
        occupied.add(at(item));
      }
    }

    var original = plans.items(planId);
    for (PlanItem item : original) occupied.add(at(item));
    List<Conflict> conflicts = new ArrayList<>();
    List<Item> draftItems = new ArrayList<>();
    int remaining = original.size();
    boolean changed = false;
    for (PlanItem item : original) {
      List<Match> matches = new ArrayList<>();
      for (PriorityItem entry : priorityByDate.getOrDefault(item.date, List.of())) {
        Match match = match(item, entry);
        if (match != null) matches.add(match);
      }
      if (matches.isEmpty()) {
        draftItems.add(snapshot(item, item.time, item.reminderTime));
        continue;
      }

      boolean duplicate = matches.stream().anyMatch(Match::duplicate);
      String suggestion;
      if (item.completed || !item.date.atTime(item.time).isAfter(now)) {
        suggestion = "已打卡或已到执行时间，保留原安排";
        draftItems.add(snapshot(item, item.time, item.reminderTime));
      } else if (duplicate && remaining > 1) {
        suggestion = "移除此任务，保留优先计划的同日同名任务";
        remaining--;
        changed = true;
      } else if (duplicate) {
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
      for (Match match : matches) conflicts.add(conflict(planId, item, match, suggestion));
    }
    Content draft = changed ? new Content(plan.title, plan.reason, plan.startDate, plan.endDate, draftItems) : null;
    return new Preview(plan.version, Math.max(plan.version, plans.latestRevision(plan.id)),
        conflicts, priorityConflicts, draft, "多目标协调：按优先计划与可用时段调整冲突任务");
  }

  private record PriorityItem(long planId, PlanItem item) {}
  private record Match(PriorityItem entry, boolean duplicate, boolean sameMinute) {}

  private static Match match(PlanItem item, PriorityItem entry) {
    boolean duplicate = entry.item.title.strip().equalsIgnoreCase(item.title.strip());
    boolean sameMinute = at(entry.item).equals(at(item));
    return duplicate || sameMinute ? new Match(entry, duplicate, sameMinute) : null;
  }

  private static Conflict conflict(long planId, PlanItem item, Match match, String suggestion) {
    var other = match.entry();
    String reason = match.duplicate() ? (match.sameMinute() ? "同日同名、同一分钟" : "同日同名") : "同一分钟";
    return new Conflict(item.id, item.title, item.date.atTime(item.time).toString(), other.planId,
        other.item.id, other.item.title, other.item.date.atTime(other.item.time).toString(), reason, suggestion, planId);
  }

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
