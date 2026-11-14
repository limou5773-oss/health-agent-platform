package com.health.service.impl;

import com.health.api.ApiException;
import com.health.api.BaselineRequests;
import com.health.api.PlanReviewRequests.Query;
import com.health.api.PlanReviewRequests.OutcomeQuery;
import com.health.mapper.PlanMapper;
import com.health.mapper.AccountMapper;
import com.health.security.Identity;
import com.health.model.PlanReviewModels.Review;
import com.health.model.PlanReviewModels.Outcome;
import com.health.service.AccountService;
import com.health.service.BaselineService;
import com.health.service.PlanReviewService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanReviewServiceImpl implements PlanReviewService {
  private final PlanMapper plans;
  private final AccountService accounts;
  private final BaselineService baseline;
  private final AccountMapper users;

  public PlanReviewServiceImpl(PlanMapper plans, AccountService accounts, BaselineService baseline, AccountMapper users) {
    this.plans = plans;
    this.accounts = accounts;
    this.baseline = baseline;
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public Review review(Long userId, Long planId, Query query) {
    var plan = plans.get(userId, planId);
    if (plan == null) throw ApiException.missing();
    LocalDate today = today(userId);
    if (!query.endDate().isBefore(today))
      throw new ApiException(400, "REVIEW_PERIOD_OPEN", "请为复盘选择已结束的日期范围");

    var items = plans.reviewItems(planId, query.startDate(), query.endDate());
    var missed = items.stream().filter(item -> !item.completed()).toList();
    int completed = items.size() - missed.size();
    BigDecimal rate = items.isEmpty() ? null : BigDecimal.valueOf(completed * 100L)
        .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);

    LocalDate previousEnd = query.startDate().minusDays(1);
    long span = ChronoUnit.DAYS.between(query.startDate(), query.endDate());
    var comparison = baseline.compare(userId, new BaselineRequests.Query(
        query.metric(), query.sourceName(), query.startDate(), query.endDate(),
        previousEnd.minusDays(span), previousEnd));
    return new Review(plan.version, today, items.size(), completed, rate, missed, comparison);
  }

  @Override
  @Transactional(readOnly = true)
  public Outcome outcome(Long userId, Long planId, OutcomeQuery query) {
    var plan = plans.get(userId, planId);
    if (plan == null) throw ApiException.missing();
    long days = ChronoUnit.DAYS.between(plan.startDate, plan.endDate) + 1;
    LocalDate afterEnd = plan.endDate.plusDays(days);
    if (!afterEnd.isBefore(today(userId)))
      throw new ApiException(409, "OUTCOME_PERIOD_OPEN", "计划后观察期截至 " + afterEnd + "，请在该日结束后查看对比");

    var items = plans.reviewItems(planId, plan.startDate, plan.endDate);
    int completed = (int) items.stream().filter(item -> item.completed()).count();
    BigDecimal rate = items.isEmpty() ? null : BigDecimal.valueOf(completed * 100L)
        .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    var comparison = baseline.compare(userId, new BaselineRequests.Query(query.metric(), query.sourceName(),
        plan.endDate.plusDays(1), afterEnd, plan.startDate.minusDays(days), plan.startDate.minusDays(1)));
    return new Outcome(plan.version, plan.startDate, plan.endDate, items.size(), completed, rate, comparison);
  }

  @Override
  @Transactional
  public void missedReason(Long userId, Long itemId, String reason) {
    Identity.requireExisting(users.lockUser(userId));
    var item = plans.itemOwner(userId, itemId);
    if (item == null) throw ApiException.missing();
    plans.lock(userId, item.planId);
    if (plans.currentItem(itemId) == null) throw ApiException.conflict("该任务已被计划修订替换");
    if (!item.date.isBefore(today(userId)) || plans.checkin(itemId) != null)
      throw ApiException.conflict("仅可为已过期且未打卡的任务填写原因");
    plans.saveMissedReason(itemId, reason.strip());
  }

  private LocalDate today(Long userId) {
    return LocalDate.now(ZoneId.of(accounts.profile(userId).timezone));
  }
}
