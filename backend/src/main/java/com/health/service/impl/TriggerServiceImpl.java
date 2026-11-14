package com.health.service.impl;

import com.health.security.Identity;
import com.health.api.Requests.TaskCreate;
import com.health.mapper.TaskMapper;
import com.health.mapper.TriggerMapper;
import com.health.model.TriggerModels.*;
import com.health.service.AccountService;
import com.health.service.TaskService;
import com.health.service.SystemSettingsService;
import com.health.service.TriggerService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TriggerServiceImpl implements TriggerService {
  private static final Logger log = LoggerFactory.getLogger(TriggerServiceImpl.class);
  private final TriggerMapper mapper;
  private final TaskMapper taskMapper;
  private final TaskService tasks;
  private final AccountService accounts;
  private final SystemSettingsService systemSettings;
  private final TransactionTemplate transaction;

  public TriggerServiceImpl(TriggerMapper mapper, TaskMapper taskMapper, TaskService tasks,
      AccountService accounts, SystemSettingsService systemSettings, PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.taskMapper = taskMapper;
    this.tasks = tasks;
    this.accounts = accounts;
    this.systemSettings = systemSettings;
    this.transaction = new TransactionTemplate(manager);
  }

  @Override
  public Settings settings(Long userId) {
    Settings saved = mapper.settings(userId);
    return saved == null ? new Settings(false, false, false, 7, null, false) : saved;
  }

  @Override
  @Transactional
  public Settings save(Long userId, Settings settings) {
    Identity.requireExisting(taskMapper.lockUser(userId));
    mapper.save(userId, settings);
    return settings;
  }

  @Override
  public List<Run> runs(Long userId) {
    return mapper.runs(userId);
  }

  @Override
  @Transactional
  public void sleepImported(Long userId, Long batchId, Map<String, LocalDate> latestBySource) {
    if (latestBySource.isEmpty()) return;
    Identity.requireExisting(taskMapper.lockUser(userId));
    Settings settings = settings(userId);
    if (!settings.importEnabled() || !systemSettings.get().importAnalysisEnabled()
        || !taskMapper.enabled(userId)) return;
    var profile = accounts.profile(userId);
    if (!profile.aiConsent || !profile.aiSleep) return;
    LocalDate end = settings.sourceName() == null
        ? latestBySource.values().stream().max(Comparator.naturalOrder()).orElseThrow()
        : latestBySource.get(settings.sourceName());
    if (end != null) {
      create(userId, Kind.SLEEP_IMPORT, batchId.toString(), end.minusDays(settings.importDays() - 1L), end, settings);
    }
  }

  @Scheduled(fixedDelay = 60_000)
  public void schedule() {
    for (Long userId : mapper.reviewUsers()) {
      try {
        transaction.executeWithoutResult(status -> {
          if (taskMapper.lockUser(userId) == null) return;
          Settings settings = settings(userId);
          var profile = accounts.profile(userId);
          if (!profile.aiConsent || !profile.aiSleep || !taskMapper.enabled(userId)) return;
          var platform = systemSettings.get();
          LocalDate today = LocalDate.now(ZoneId.of(profile.timezone));
          if (settings.weeklyEnabled() && platform.weeklyEnabled()) {
            LocalDate end = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);
            create(userId, Kind.WEEKLY_REVIEW, end.toString(), end.minusDays(6), end, settings);
          }
          if (settings.monthlyEnabled() && platform.monthlyEnabled()) {
            LocalDate end = today.withDayOfMonth(1).minusDays(1);
            create(userId, Kind.MONTHLY_REVIEW, end.toString(), end.withDayOfMonth(1), end, settings);
          }
        });
      } catch (RuntimeException e) {
        log.error("Periodic review trigger failed for user {}", userId, e);
      }
    }
  }

  private void create(Long userId, Kind kind, String key, LocalDate start, LocalDate end, Settings settings) {
    if (mapper.exists(userId, kind, key, start, end)) return;
    String goal = switch (kind) {
      case WEEKLY_REVIEW -> "复盘上一周的睡眠记录，说明睡眠趋势、覆盖和可执行的后续行动。";
      case MONTHLY_REVIEW -> "复盘上一个自然月的睡眠记录，说明睡眠趋势、覆盖和可执行的后续行动。";
      case SLEEP_IMPORT -> "分析本次睡眠导入覆盖的近期记录，说明趋势与数据覆盖。";
    };
    var task = tasks.create(userId, new TaskCreate(
        goal, kind == Kind.SLEEP_IMPORT ? "ANALYZE" : "REPORT", start, end, settings.sourceName(), null, null, false,
        null, null, null, null));
    mapper.insert(userId, kind, key, start, end, settings.sourceName(), task.id);
    if (settings.notifyInApp()) mapper.notifyRun(userId, kind, task.id);
  }
}
