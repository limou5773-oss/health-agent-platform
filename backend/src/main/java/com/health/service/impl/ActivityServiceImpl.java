package com.health.service.impl;

import com.health.security.Identity;
import com.health.model.ImportAdminModels.Kind;
import com.health.service.ImportAdminService;
import com.health.api.*;
import com.health.mapper.ActivityMapper;
import com.health.mapper.PlanMapper;
import com.health.model.ActivityModels.*;
import com.health.model.Models.Page;
import com.health.model.Models.ReportSourceChange;
import com.health.service.AccountService;
import com.health.service.ActivityService;
import com.health.service.AppleHealthExportReader;
import com.health.service.NotificationService;
import com.health.service.TaskService;
import jakarta.validation.Validator;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ActivityServiceImpl implements ActivityService {
  private static final DateTimeFormatter APPLE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xx")
      .withResolverStyle(ResolverStyle.STRICT);
  private static final Pattern APPLE_STEP_VALUE = Pattern.compile("[0-9]+(?:\\.0+)?");
  private final ActivityMapper mapper;
  private final PlanMapper plans;
  private final AccountService accounts;
  private final Validator validator;
  private final NotificationService notifications;
  private final ImportAdminService importAdmin;
  private final TaskService tasks;
  private final AppleHealthExportReader apple;

  public ActivityServiceImpl(ActivityMapper mapper, PlanMapper plans, AccountService accounts,
      Validator validator, NotificationService notifications,
      ImportAdminService importAdmin, TaskService tasks, AppleHealthExportReader apple) {
    this.mapper = mapper;
    this.plans = plans;
    this.accounts = accounts;
    this.validator = validator;
    this.notifications = notifications;
    this.importAdmin = importAdmin;
    this.tasks = tasks;
    this.apple = apple;
  }

  public Page<StepRecord> steps(Long uid, Requests.SleepQuery q) {
    return new Page<>(mapper.steps(uid, q, (q.page - 1L) * q.pageSize), mapper.stepCount(uid, q), q.page, q.pageSize);
  }

  @Transactional
  public StepRecord createStep(Long uid, ActivityRequests.Step request) {
    Identity.requireExisting(mapper.lockUser(uid));
    StepRecord record = stepRecord(uid, request);
    checkStep(record, mapper.stepDay(uid, record.activityDate, record.sourceName), false);
    mapper.insertStep(record);
    return mapper.step(uid, record.id);
  }

  @Transactional
  public StepRecord updateStep(Long uid, Long id, ActivityRequests.Step request) {
    Identity.requireExisting(mapper.lockUser(uid));
    StepRecord existing = mapper.step(uid, id);
    if (existing == null) throw ApiException.missing();
    StepRecord record = stepRecord(uid, request);
    record.id = id;
    record.sourceName = existing.sourceName;
    checkStep(record, mapper.stepDay(uid, record.activityDate, record.sourceName), false);
    mapper.updateStep(record);
    tasks.invalidateCombined(uid, existing.activityDate, existing.activityDate);
    plans.invalidateHealthReports(uid, existing.activityDate, existing.activityDate, ReportSourceChange.CORRECTED);
    if (!record.activityDate.equals(existing.activityDate)) {
      tasks.invalidateCombined(uid, record.activityDate, record.activityDate);
      plans.invalidateHealthReports(uid, record.activityDate, record.activityDate, ReportSourceChange.CORRECTED);
    }
    return mapper.step(uid, id);
  }

  @Transactional
  public void deleteStep(Long uid, Long id) {
    Identity.requireExisting(mapper.lockUser(uid));
    StepRecord existing = mapper.step(uid, id);
    if (existing == null) throw ApiException.missing();
    mapper.deleteStep(uid, id);
    tasks.invalidateCombined(uid, existing.activityDate, existing.activityDate);
    plans.invalidateHealthReports(uid, existing.activityDate, existing.activityDate, ReportSourceChange.DELETED);
  }

  public List<String> stepSources(Long uid) { return mapper.stepSources(uid); }

  public Goal goal(Long uid) { return new Goal(mapper.goal(uid)); }

  @Transactional
  public Goal updateGoal(Long uid, ActivityRequests.Goal request) {
    Identity.requireExisting(mapper.lockUser(uid));
    mapper.saveGoal(uid, request.dailySteps().intValueExact());
    return new Goal(request.dailySteps().intValueExact());
  }

  public StepStatistics stepStatistics(Long uid, LocalDate start, LocalDate end, String source) {
    List<StepRecord> samples = mapper.stepRange(uid, start, end, source);
    LocalDateTime dataCutoff = samples.stream().map(record -> record.endTime).max(LocalDateTime::compareTo).orElse(null);
    Map<LocalDate, List<StepRecord>> days = samples.stream()
        .collect(Collectors.groupingBy(record -> record.activityDate));
    Integer goal = mapper.goal(uid);
    List<StepDaily> daily = new ArrayList<>();
    long total = 0, valid = 0, goalDays = 0;
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      List<StepRecord> records = days.get(date);
      if (records == null) {
        daily.add(new StepDaily(date, null, null, null));
        continue;
      }
      if (records.stream().map(record -> record.sourceName).distinct().count() > 1)
        throw new ApiException(409, "SOURCE_SELECTION_REQUIRED", "同一天存在多个步数来源，请先选择来源");
      long steps = "CUMULATIVE".equals(records.getFirst().mode)
          ? records.stream().max(Comparator.comparing(record -> record.endTime)).orElseThrow().steps
          : records.stream().mapToLong(record -> record.steps).sum();
      total += steps;
      valid++;
      if (goal != null && steps >= goal) goalDays++;
      daily.add(new StepDaily(date, steps, records.getFirst().sourceName,
          goal == null ? null : round(steps * 100.0 / goal)));
    }
    long period = ChronoUnit.DAYS.between(start, end) + 1;
    return new StepStatistics(start, end, source, period, valid, round(valid * 100.0 / period),
        total, valid == 0 ? null : round(total * 1.0 / valid), goal, goal == null ? null : goalDays, dataCutoff, daily);
  }

  private static StepRecord stepRecord(Long uid, ActivityRequests.Step request) {
    StepRecord record = new StepRecord();
    record.userId = uid;
    record.startTime = request.startTime();
    record.endTime = request.endTime();
    record.activityDate = request.startTime().toLocalDate();
    record.steps = request.steps().intValueExact();
    record.mode = request.mode();
    record.sourceName = request.sourceName();
    record.note = request.note();
    return record;
  }

  // A bucket contains only records for this user, source and local date.
  private static boolean checkStep(StepRecord candidate, List<StepRecord> bucket, boolean skipIdentical) {
    for (StepRecord existing : bucket) {
      if (candidate.id != null && candidate.id.equals(existing.id)) continue;
      if (existing.startTime.equals(candidate.startTime) && existing.endTime.equals(candidate.endTime)) {
        if (skipIdentical && existing.steps == candidate.steps && existing.mode.equals(candidate.mode)) return true;
        throw new ApiException(409, "DUPLICATE_RECORD", "同来源、起止时间的步数记录已存在且不能重复；更正请修改原记录");
      }
      if (!existing.mode.equals(candidate.mode))
        throw ApiException.conflict("同一来源同一天不能混用累计和增量步数");
      if ("DELTA".equals(candidate.mode) && candidate.startTime.isBefore(existing.endTime)
          && existing.startTime.isBefore(candidate.endTime))
        throw ApiException.conflict("同一来源同一天的增量步数区间不能重叠");
    }
    return false;
  }

  @Transactional
  public StepImportBatch preview(Long uid, MultipartFile file, String timezone) {
    Identity.requireExisting(mapper.lockUser(uid));
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_CSV", "CSV不能为空且不能超过10MB");
    ZoneId profileZone = ZoneId.of(accounts.profile(uid).timezone);
    String inputZone = timezone == null || timezone.isBlank() ? profileZone.getId() : timezone;
    if (!ZoneId.getAvailableZoneIds().contains(inputZone))
      throw new ApiException(400, "INVALID_TIMEZONE", "无效IANA时区");
    ZoneId zone = ZoneId.of(inputZone);
    StepImportBatch batch = new StepImportBatch();
    batch.userId = uid;
    batch.status = "PREVIEW";
    batch.importFormat = StepImportFormat.CSV;
    batch.rows = new ArrayList<>();
    Map<StepDay, List<StepRecord>> buckets = new HashMap<>();
    try (Reader reader = new InputStreamReader(file.getInputStream(),
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT));
        CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(false).get().parse(reader)) {
      if (!Kind.STEPS.matches(parser.getHeaderNames()))
        throw new ApiException(400, "INVALID_CSV_HEADER", "CSV表头须为startTime,endTime,steps,mode,sourceName,note");
      for (CSVRecord csv : parser) {
        if (batch.rows.size() >= 10000) throw new ApiException(400, "TOO_MANY_ROWS", "CSV最多10000行");
        StepImportRow row = new StepImportRow();
        row.rowNumber = (int) csv.getRecordNumber() + 1;
        row.startTime = csv.size() > 0 ? csv.get(0) : "";
        row.endTime = csv.size() > 1 ? csv.get(1) : "";
        row.steps = csv.size() > 2 ? csv.get(2) : "";
        row.mode = csv.size() > 3 ? csv.get(3) : "";
        row.sourceName = csv.size() > 4 ? csv.get(4) : "";
        row.note = csv.size() > 5 ? csv.get(5) : "";
        if (csv.size() != 6) row.error = "列数须为6";
        else {
          try {
            ActivityRequests.Step request = new ActivityRequests.Step(
                parseTime(row.startTime, zone, profileZone), parseTime(row.endTime, zone, profileZone),
                BigDecimal.valueOf(Integer.parseInt(row.steps)), row.mode, row.sourceName, row.note);
            validatePreviewStep(batch, row, buckets, request);
          } catch (DateTimeException | IllegalArgumentException e) {
            row.error = "时间须为ISO本地时间或带offset时间；步数须为非负整数";
          } catch (ApiException e) {
            row.error = e.getMessage();
          }
        }
        addPreviewRow(batch, row);
      }
    } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
      throw new ApiException(400, "INVALID_CSV", "无法解析UTF-8 CSV，请检查编码、表头及引号格式");
    }
    return saveStepPreview(batch);
  }

  @Transactional
  public StepImportBatch previewApple(Long uid, MultipartFile file) {
    Identity.requireExisting(mapper.lockUser(uid));
    ZoneId zone = ZoneId.of(accounts.profile(uid).timezone);
    StepImportBatch batch = new StepImportBatch();
    batch.userId = uid;
    batch.status = "PREVIEW";
    batch.importFormat = StepImportFormat.APPLE_HEALTH;
    batch.rows = new ArrayList<>();
    Map<StepDay, List<StepRecord>> buckets = new HashMap<>();
    apple.read(file, xml -> {
      if (!"HKQuantityTypeIdentifierStepCount".equals(xml.getAttributeValue(null, "type"))) {
        batch.skippedRows++;
        return;
      }
      if (batch.rows.size() >= 10000)
        throw new ApiException(400, "TOO_MANY_ROWS", "苹果健康文件最多导入10000条步数记录");
      StepImportRow row = new StepImportRow();
      row.rowNumber = batch.rows.size() + batch.skippedRows + 1;
      row.startTime = Objects.toString(xml.getAttributeValue(null, "startDate"), "");
      row.endTime = Objects.toString(xml.getAttributeValue(null, "endDate"), "");
      row.steps = Objects.toString(xml.getAttributeValue(null, "value"), "");
      row.mode = "DELTA";
      String source = xml.getAttributeValue(null, "sourceName");
      row.sourceName = source == null || source.isBlank() ? "" : "苹果健康 · " + source;
      if (!"count".equals(xml.getAttributeValue(null, "unit"))) row.error = "步数单位须为 count";
      else if (row.steps.length() > 32 || !APPLE_STEP_VALUE.matcher(row.steps).matches())
        row.error = "步数须为非负整数";
      else {
        try {
          OffsetDateTime start = OffsetDateTime.parse(row.startTime, APPLE_TIME);
          OffsetDateTime end = OffsetDateTime.parse(row.endTime, APPLE_TIME);
          ZonedDateTime localStart = start.atZoneSameInstant(zone);
          ZonedDateTime localEnd = end.atZoneSameInstant(zone);
          if (!localStart.getOffset().equals(localEnd.getOffset())
              || zone.getRules().getValidOffsets(localStart.toLocalDateTime()).size() != 1
              || zone.getRules().getValidOffsets(localEnd.toLocalDateTime()).size() != 1) {
            row.error = "档案时区存在偏移切换或时间歧义，请改用 UTC 时区后重新导入";
          } else {
            BigDecimal steps = new BigDecimal(row.steps).setScale(0);
            row.note = "苹果健康原始区间 " + row.startTime + " 至 " + row.endTime;
            ActivityRequests.Step request = new ActivityRequests.Step(
                localStart.toLocalDateTime(), localEnd.toLocalDateTime(), steps, row.mode, row.sourceName, row.note);
            validatePreviewStep(batch, row, buckets, request);
            if (row.valid) row.steps = steps.toPlainString();
          }
        } catch (DateTimeException e) {
          row.error = "苹果健康记录的起止时间须包含有效日期、时间及UTC偏移";
        } catch (ApiException e) {
          row.error = e.getMessage();
        }
      }
      addPreviewRow(batch, row);
    });
    if (batch.rows.isEmpty())
      throw new ApiException(400, "APPLE_METRIC_MISSING",
          "文件中没有步数记录，已跳过 " + batch.skippedRows + " 条其他记录");
    return saveStepPreview(batch);
  }

  private void validatePreviewStep(StepImportBatch batch, StepImportRow row,
      Map<StepDay, List<StepRecord>> buckets, ActivityRequests.Step request) {
    var violations = validator.validate(request);
    if (!violations.isEmpty()) {
      row.error = violations.iterator().next().getMessage();
      return;
    }
    StepRecord record = stepRecord(batch.userId, request);
    List<StepRecord> bucket = buckets.computeIfAbsent(new StepDay(record.activityDate, record.sourceName),
        key -> new ArrayList<>(mapper.stepDay(batch.userId, key.date(), key.source())));
    boolean duplicate = checkStep(record, bucket, true);
    row.valid = true;
    row.startTime = record.startTime.toString();
    row.endTime = record.endTime.toString();
    if (batch.firstStartTime == null || record.startTime.isBefore(batch.firstStartTime))
      batch.firstStartTime = record.startTime;
    if (batch.lastEndTime == null || record.endTime.isAfter(batch.lastEndTime))
      batch.lastEndTime = record.endTime;
    if (duplicate) {
      row.error = "重复记录，确认时跳过";
      batch.duplicateRows++;
    } else bucket.add(record);
  }

  private static void addPreviewRow(StepImportBatch batch, StepImportRow row) {
    if (row.valid) batch.validRows++;
    else {
      batch.invalidRows++;
      row.error = "第" + row.rowNumber + "行：" + row.error;
    }
    batch.rows.add(row);
  }

  private StepImportBatch saveStepPreview(StepImportBatch batch) {
    batch.totalRows = batch.rows.size();
    mapper.insertBatch(batch);
    for (StepImportRow row : batch.rows) {
      row.batchId = batch.id;
      mapper.insertRow(row);
    }
    batch.createdAt = mapper.batch(batch.userId, batch.id).createdAt;
    notifications.importIssues(batch.userId, batch.id, true, batch.validRows, batch.invalidRows);
    return batch;
  }

  private record StepDay(LocalDate date, String source) {}

  private static LocalDateTime parseTime(String value, ZoneId zone, ZoneId profileZone) {
    var parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(value, OffsetDateTime::from, LocalDateTime::from);
    return parsed instanceof OffsetDateTime offset
        ? offset.atZoneSameInstant(profileZone).toLocalDateTime()
        : ((LocalDateTime) parsed).atZone(zone).withZoneSameInstant(profileZone).toLocalDateTime();
  }

  @Transactional
  public StepImportBatch confirm(Long uid, Long id) {
    Identity.requireExisting(mapper.lockUser(uid));
    StepImportBatch batch = mapper.batch(uid, id);
    if (batch == null) throw ApiException.missing();
    if ("CONFIRMED".equals(batch.status)) return batch;
    batch.duplicateRows = 0;
    Map<StepDay, List<StepRecord>> buckets = new HashMap<>();
    for (StepImportRow row : mapper.rows(id)) {
      if (!row.valid) continue;
      StepRecord record = stepRecord(uid, new ActivityRequests.Step(LocalDateTime.parse(row.startTime),
          LocalDateTime.parse(row.endTime), BigDecimal.valueOf(Integer.parseInt(row.steps)), row.mode, row.sourceName, row.note));
      List<StepRecord> bucket = buckets.computeIfAbsent(new StepDay(record.activityDate, record.sourceName),
          key -> new ArrayList<>(mapper.stepDay(uid, key.date(), key.source())));
      if (checkStep(record, bucket, true)) batch.duplicateRows++;
      else {
        mapper.insertStep(record);
        bucket.add(record);
        batch.insertedRows++;
      }
    }
    batch.status = "CONFIRMED";
    mapper.confirmBatch(batch);
    importAdmin.snapshotIssues(Kind.STEPS, id);
    mapper.deleteRows(id);
    return batch;
  }

  public List<StepImportBatch> imports(Long uid) { return mapper.batches(uid); }

  public Page<Workout> workouts(Long uid, Requests.SleepQuery q) {
    return new Page<>(mapper.workouts(uid, q, (q.page - 1L) * q.pageSize), mapper.workoutCount(uid, q), q.page, q.pageSize);
  }

  @Transactional
  public Workout createWorkout(Long uid, ActivityRequests.Workout request) {
    Identity.requireExisting(mapper.lockUser(uid));
    Workout record = workoutRecord(uid, request);
    validatePlan(uid, record);
    if (mapper.workoutDuplicate(record) != null) throw new ApiException(409, "DUPLICATE_RECORD", "相同起止时间、类型及来源的运动记录已存在");
    mapper.insertWorkout(record);
    return mapper.workout(uid, record.id);
  }

  @Transactional
  public Workout updateWorkout(Long uid, Long id, ActivityRequests.Workout request) {
    Identity.requireExisting(mapper.lockUser(uid));
    Workout existing = mapper.workout(uid, id);
    if (existing == null) throw ApiException.missing();
    Workout record = workoutRecord(uid, request);
    record.id = id;
    if (!Objects.equals(existing.planItemId, record.planItemId)
        || !existing.activityDate.equals(record.activityDate)) validatePlan(uid, record);
    if (mapper.workoutDuplicate(record) != null) throw new ApiException(409, "DUPLICATE_RECORD", "相同起止时间、类型及来源的运动记录已存在");
    mapper.updateWorkout(record);
    LocalDate previousEnd = existing.endTime.minusNanos(1).toLocalDate();
    LocalDate currentEnd = record.endTime.minusNanos(1).toLocalDate();
    tasks.invalidateCombined(uid, existing.activityDate, previousEnd);
    plans.invalidateHealthReports(uid, existing.activityDate, previousEnd, ReportSourceChange.CORRECTED);
    if (record.activityDate.isBefore(existing.activityDate) || currentEnd.isAfter(previousEnd)) {
      tasks.invalidateCombined(uid, record.activityDate, currentEnd);
      plans.invalidateHealthReports(uid, record.activityDate, currentEnd, ReportSourceChange.CORRECTED);
    }
    return mapper.workout(uid, id);
  }

  private void validatePlan(Long uid, Workout record) {
    if (record.planItemId == null) return;
    var item = plans.itemOwner(uid, record.planItemId);
    if (item == null) throw ApiException.missing();
    var plan = plans.lock(uid, item.planId);
    if (plans.currentItem(record.planItemId) == null) throw ApiException.conflict("该任务已被计划修订替换");
    if (!"ACTIVE".equals(plan.status)) throw ApiException.conflict("只能关联进行中计划的任务");
    if (!record.activityDate.equals(item.date))
      throw new ApiException(400, "DATE_MISMATCH", "关联任务日期须与运动开始日期一致");
  }

  private static Workout workoutRecord(Long uid, ActivityRequests.Workout request) {
    Workout record = new Workout();
    record.userId = uid;
    record.exerciseType = request.exerciseType();
    record.startTime = request.startTime();
    record.endTime = request.endTime();
    record.activityDate = record.startTime.toLocalDate();
    record.durationMinutes = ChronoUnit.SECONDS.between(record.startTime, record.endTime) / 60.0;
    record.sourceName = request.sourceName();
    record.note = request.note();
    record.planItemId = request.planItemId();
    return record;
  }

  @Transactional
  public void deleteWorkout(Long uid, Long id) {
    Identity.requireExisting(mapper.lockUser(uid));
    Workout existing = mapper.workout(uid, id);
    if (existing == null) throw ApiException.missing();
    mapper.deleteWorkout(uid, id);
    tasks.invalidateCombined(uid, existing.activityDate, existing.endTime.minusNanos(1).toLocalDate());
    plans.invalidateHealthReports(uid, existing.activityDate, existing.endTime.minusNanos(1).toLocalDate(), ReportSourceChange.DELETED);
  }

  public List<String> workoutSources(Long uid) { return mapper.workoutSources(uid); }

  public List<EligiblePlanItem> planItems(Long uid, LocalDate date) { return mapper.planItems(uid, date); }

  public WorkoutStatistics workoutStatistics(Long uid, LocalDate start, LocalDate end, String source) {
    LocalDateTime windowStart = start.atStartOfDay(), windowEnd = end.plusDays(1).atStartOfDay();
    List<Workout> records = mapper.workoutRange(uid, windowStart, windowEnd, source);
    Map<LocalDate, List<Interval>> days = new HashMap<>();
    Map<String, LocalDateTime> sourceEnds = new HashMap<>();
    // The mapper orders by start time. Compare active source ends to detect actual cross-source overlap.
    for (Workout record : records) {
      LocalDateTime from = record.startTime.isBefore(windowStart) ? windowStart : record.startTime;
      LocalDateTime to = record.endTime.isAfter(windowEnd) ? windowEnd : record.endTime;
      if (source == null || source.isBlank()) {
        for (var entry : sourceEnds.entrySet())
          if (!entry.getKey().equals(record.sourceName) && entry.getValue().isAfter(from))
            throw new ApiException(409, "SOURCE_SELECTION_REQUIRED", "不同来源的运动时段重叠，请先选择来源");
        sourceEnds.merge(record.sourceName, to, (a, b) -> a.isAfter(b) ? a : b);
      }
      for (LocalDateTime cursor = from; cursor.isBefore(to); ) {
        LocalDateTime nextDay = cursor.toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime sliceEnd = to.isBefore(nextDay) ? to : nextDay;
        days.computeIfAbsent(cursor.toLocalDate(), key -> new ArrayList<>()).add(new Interval(cursor, sliceEnd));
        cursor = sliceEnd;
      }
    }
    List<WorkoutDaily> daily = new ArrayList<>();
    long totalSeconds = 0, valid = 0;
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      List<Interval> intervals = days.get(date);
      if (intervals == null) {
        daily.add(new WorkoutDaily(date, null, 0));
        continue;
      }
      intervals.sort(Comparator.comparing(Interval::start));
      LocalDateTime from = intervals.getFirst().start(), to = intervals.getFirst().end();
      long seconds = 0;
      for (int i = 1; i < intervals.size(); i++) {
        Interval interval = intervals.get(i);
        if (!interval.start().isAfter(to)) {
          if (interval.end().isAfter(to)) to = interval.end();
        } else {
          seconds += ChronoUnit.SECONDS.between(from, to);
          from = interval.start();
          to = interval.end();
        }
      }
      seconds += ChronoUnit.SECONDS.between(from, to);
      totalSeconds += seconds;
      valid++;
      daily.add(new WorkoutDaily(date, round(seconds / 60.0), intervals.size()));
    }
    long period = ChronoUnit.DAYS.between(start, end) + 1;
    return new WorkoutStatistics(start, end, source, period, valid, round(valid * 100.0 / period),
        round(totalSeconds / 60.0), records.size(), daily);
  }

  private record Interval(LocalDateTime start, LocalDateTime end) {}

  private static double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
