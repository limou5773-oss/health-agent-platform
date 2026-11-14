package com.health.service.impl;

import com.health.security.Identity;
import com.health.api.ApiException;
import com.health.api.TimelineRequests.ConfirmDelete;
import com.health.api.TimelineRequests.DeleteQuery;
import com.health.mapper.TaskMapper;
import com.health.mapper.TimelineMapper;
import com.health.model.TimelineModels.DeletionPreview;
import com.health.model.TimelineModels.DeletionSource;
import com.health.service.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthRecordDeletionServiceImpl implements HealthRecordDeletionService {
  private final TimelineMapper timeline;
  private final TaskMapper tasks;
  private final SleepService sleep;
  private final ActivityService activity;
  private final NutritionService nutrition;
  private final WeightService weight;
  private final DiaryService diary;
  private final HeartRateService heartRate;

  public HealthRecordDeletionServiceImpl(TimelineMapper timeline, TaskMapper tasks, SleepService sleep,
      ActivityService activity, NutritionService nutrition, WeightService weight, DiaryService diary,
      HeartRateService heartRate) {
    this.timeline = timeline;
    this.tasks = tasks;
    this.sleep = sleep;
    this.activity = activity;
    this.nutrition = nutrition;
    this.weight = weight;
    this.diary = diary;
    this.heartRate = heartRate;
  }

  @Override
  @Transactional(readOnly = true)
  public DeletionPreview preview(Long userId, DeleteQuery query) throws IOException {
    return selection(userId, query).preview();
  }

  @Override
  @Transactional(rollbackFor = IOException.class)
  public int delete(Long userId, ConfirmDelete query) throws IOException {
    Identity.requireExisting(tasks.lockUser(userId));
    var selection = selection(userId, query);
    if (!selection.preview().fingerprint().equals(query.getFingerprint()))
      throw new ApiException(409, "DELETE_SCOPE_CHANGED", "记录范围已变化，请重新预览后确认");
    for (Long id : selection.ids()) {
      switch (query.getType()) {
        case SLEEP -> sleep.delete(userId, id);
        case STEPS -> activity.deleteStep(userId, id);
        case WORKOUT -> activity.deleteWorkout(userId, id);
        case MEAL -> nutrition.deleteMeal(userId, id);
        case WATER -> nutrition.deleteWater(userId, id);
        case WEIGHT -> weight.deleteRecord(userId, id);
        case HEART_RATE -> heartRate.delete(userId, id);
        case DIARY -> diary.delete(userId, id);
        default -> throw new IllegalStateException("已校验的健康记录类型无效");
      }
    }
    return selection.ids().size();
  }

  private Selection selection(Long userId, DeleteQuery query) throws IOException {
    String impact = switch (query.getType()) {
      case SLEEP -> "匹配的睡眠分析、报告及相关任务会话和检查点将被清理，覆盖记录日期的本地健康报告将失效。";
      case DIARY -> "已授权日记涉及的任务会话、检查点及报告内容将被清理。";
      case HEART_RATE, STEPS, WORKOUT, WEIGHT -> "覆盖相关日期的报告及综合分析将失效，相关任务内容和检查点将清除。";
      case MEAL, WATER -> "覆盖相关日期的综合报告、专项报告和阶段对比将失效并清除内容。";
      default -> throw new ApiException(400, "INVALID_RECORD_TYPE", "只能按范围删除健康记录和日记");
    };
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    String scope = userId + "|" + query.getType() + "|" + query.startDate + "|" + query.endDate
        + "|" + (query.sourceName == null ? "" : query.sourceName);
    digest.update(scope.getBytes(StandardCharsets.UTF_8));
    List<Long> ids = new ArrayList<>();
    var sourceCounts = new TreeMap<String, Integer>();
    try (var rows = timeline.exportEvents(userId, query)) {
      for (var row : rows) {
        ids.add(row.id);
        sourceCounts.merge(row.sourceName, 1, Integer::sum);
        String record = row.id + "|" + row.eventDate + "|" + row.occurredAt + "|" + row.sourceName
            + "|" + row.detailsJson;
        digest.update(record.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
      }
    }
    List<DeletionSource> sources = sourceCounts.entrySet().stream()
        .map(entry -> new DeletionSource(entry.getKey(), entry.getValue()))
        .toList();
    return new Selection(ids,
        new DeletionPreview(ids.size(), HexFormat.of().formatHex(digest.digest()), impact, sources));
  }

  private record Selection(List<Long> ids, DeletionPreview preview) {}
}
