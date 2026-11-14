package com.health.service.impl;

import com.health.security.Identity;
import com.health.model.ImportAdminModels.Kind;
import com.health.api.*;
import com.health.mapper.SleepMapper;
import com.health.model.Models.*;
import com.health.service.*;
import java.io.*;
import java.nio.charset.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SleepServiceImpl implements SleepService {
  private final SleepMapper mapper;
  private final AccountService accounts;
  private final TaskService tasks;
  private final AppleHealthImporter appleImporter;
  private final NotificationService notifications;
  private final ImportAdminService importAdmin;
  private final TriggerService triggers;

  public SleepServiceImpl(
      SleepMapper mapper,
      AccountService accounts,
      TaskService tasks,
      AppleHealthImporter appleImporter, NotificationService notifications, TriggerService triggers,
      ImportAdminService importAdmin) {
    this.mapper = mapper;
    this.accounts = accounts;
    this.tasks = tasks;
    this.appleImporter = appleImporter;
    this.notifications = notifications;
    this.importAdmin = importAdmin;
    this.triggers = triggers;
  }

  public Page<SleepRecord> list(Long uid, Requests.SleepQuery q) {
    return new Page<>(
        mapper.list(uid, q, (q.page - 1) * q.pageSize), mapper.count(uid, q), q.page, q.pageSize);
  }

  @Transactional
  public SleepRecord create(Long uid, Requests.Sleep r) {
    Identity.requireExisting(mapper.lockUser(uid));
    SleepRecord s = record(uid, r);
    mapper.insert(s);
    return mapper.get(uid, s.id);
  }

  @Transactional
  public SleepRecord update(Long uid, Long id, Requests.Sleep r) {
    Identity.requireExisting(mapper.lockUser(uid));
    SleepRecord old = mapper.get(uid, id);
    if (old == null) throw ApiException.missing();
    SleepRecord s = record(uid, r);
    s.id = id;
    if (old.startTime.equals(s.startTime) && old.endTime.equals(s.endTime)) {
      s.sleepDate = old.sleepDate;
    }
    mapper.update(s);
    tasks.invalidate(uid, old.sleepDate, old.sourceName);
    if (!old.sleepDate.equals(s.sleepDate) || !old.sourceName.equals(s.sourceName)) {
      tasks.invalidate(uid, s.sleepDate, s.sourceName);
    }
    return mapper.get(uid, id);
  }

  @Transactional
  public void delete(Long uid, Long id) {
    Identity.requireExisting(mapper.lockUser(uid));
    SleepRecord s = mapper.get(uid, id);
    if (s == null) throw ApiException.missing();
    mapper.delete(uid, id);
    tasks.invalidate(uid, s.sleepDate, s.sourceName);
  }

  public List<String> sources(Long uid) {
    return mapper.sources(uid);
  }

  public Statistics statistics(Long uid, LocalDate start, LocalDate end, String source) {
    List<SleepRecord> records = mapper.range(uid, start, end, source);
    Map<LocalDate, List<SleepRecord>> grouped =
        records.stream().collect(Collectors.groupingBy(s -> s.sleepDate));
    if (source == null || source.isBlank())
      for (List<SleepRecord> day : grouped.values())
        for (int i = 0; i < day.size(); i++)
          for (int j = i + 1; j < day.size(); j++) {
            SleepRecord a = day.get(i), b = day.get(j);
            if (!a.sourceName.equals(b.sourceName)
                && a.startTime.isBefore(b.endTime)
                && b.startTime.isBefore(a.endTime))
              throw new ApiException(409, "SOURCE_SELECTION_REQUIRED", "不同来源的睡眠时段重叠，请先选择来源");
          }
    List<Daily> daily = new ArrayList<>();
    double total = 0;
    long valid = 0;
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      List<SleepRecord> day = grouped.get(date);
      if (day == null) {
        daily.add(new Daily(date, null, 0));
        continue;
      }
      day.sort(Comparator.comparing(s -> s.startTime));
      long seconds = 0;
      LocalDateTime from = day.getFirst().startTime, to = day.getFirst().endTime;
      for (int i = 1; i < day.size(); i++) {
        SleepRecord s = day.get(i);
        if (!s.startTime.isAfter(to)) {
          if (s.endTime.isAfter(to)) to = s.endTime;
        } else {
          seconds += ChronoUnit.SECONDS.between(from, to);
          from = s.startTime;
          to = s.endTime;
        }
      }
      seconds += ChronoUnit.SECONDS.between(from, to);
      double hours = seconds / 3600.0;
      total += hours;
      valid++;
      daily.add(new Daily(date, round(hours), day.size()));
    }
    long period = ChronoUnit.DAYS.between(start, end) + 1;
    return new Statistics(
        start,
        end,
        source,
        period,
        valid,
        round(valid * 100.0 / period),
        valid == 0 ? null : round(total / valid),
        round(total),
        records.size(),
        records.stream().map(s -> s.endTime).max(Comparator.naturalOrder()).orElse(null),
        daily);
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  public byte[] export(Long uid, Requests.SleepQuery q) {
    StringWriter text = new StringWriter();
    try (CSVPrinter csv = new CSVPrinter(text, CSVFormat.DEFAULT)) {
      csv.printRecord("startTime", "endTime", "sourceName", "note");
      for (SleepRecord r : mapper.range(uid, q.startDate, q.endDate, q.sourceName))
        csv.printRecord(r.startTime, r.endTime, safeCell(r.sourceName), safeCell(r.note));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return ("\uFEFF" + text).getBytes(StandardCharsets.UTF_8);
  }

  private static String safeCell(String s) {
    return s.matches("(?s)^[\\s]*[=+@-].*")
            || s.startsWith("\t")
            || s.startsWith("\r")
            || s.startsWith("\n")
        ? "'" + s
        : s;
  }

  @Transactional
  public ImportBatch preview(Long uid, MultipartFile file, Requests.ImportOptions options) {
    Identity.requireExisting(mapper.lockUser(uid));
    ImportBatch batch =
        "CSV".equals(options.format)
            ? parseCsv(uid, file, options.timezone)
            : appleImporter.parse(file, ZoneId.of(accounts.profile(uid).timezone));
    batch.sourceApp = options.sourceApp;
    batch.deviceBrand = options.deviceBrand;
    batch.deviceType = options.deviceType;
    batch.deviceModel = options.deviceModel.strip();
    return savePreview(uid, batch);
  }

  private ImportBatch parseCsv(Long uid, MultipartFile file, String timezone) {
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_CSV", "CSV不能为空且不能超过10MB");
    String profileZoneName = accounts.profile(uid).timezone;
    String zoneName = timezone == null || timezone.isBlank() ? profileZoneName : timezone;
    if (!ZoneId.getAvailableZoneIds().contains(zoneName))
      throw new ApiException(400, "INVALID_TIMEZONE", "无效IANA时区");
    ZoneId zone = ZoneId.of(zoneName), profileZone = ZoneId.of(profileZoneName);
    List<ImportRow> rows = new ArrayList<>();
    ImportBatch batch = new ImportBatch();
    batch.format = "CSV";
    try (Reader reader =
            new InputStreamReader(
                file.getInputStream(),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT));
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)
                .get()
                .parse(reader)) {
      if (!Kind.SLEEP.matches(parser.getHeaderNames()))
        throw new ApiException(
            400, "INVALID_CSV_HEADER", "CSV表头须为startTime,endTime,sourceName,note");
      for (CSVRecord csv : parser) {
        if (rows.size() >= 10000) throw new ApiException(400, "TOO_MANY_ROWS", "CSV最多10000行");
        ImportRow row = new ImportRow();
        row.rowNumber = (int) csv.getRecordNumber() + 1;
        row.startTime = csv.size() > 0 ? csv.get(0) : "";
        row.endTime = csv.size() > 1 ? csv.get(1) : "";
        row.sourceName = csv.size() > 2 ? csv.get(2) : "";
        row.note = csv.size() > 3 ? csv.get(3) : "";
        try {
          if (csv.size() != 4) throw new IllegalArgumentException("列数须为4");
          if (row.sourceName.isBlank()
              || row.sourceName.length() > 100
              || row.note.length() > 1000
              || row.startTime.length() > 100
              || row.endTime.length() > 100)
            throw new IllegalArgumentException("来源必填且不超过100字符，备注最多1000字符，时间最多100字符");
          LocalDateTime start = parseTime(row.startTime, zone, profileZone),
              end = parseTime(row.endTime, zone, profileZone);
          if (ChronoUnit.SECONDS.between(start, end) <= 0)
            throw new IllegalArgumentException("睡眠结束须晚于开始且至少1秒");
          row.startTime = start.toString();
          row.endTime = end.toString();
          row.sleepDate = end.toLocalDate();
          row.valid = true;
          batch.validRows++;
        } catch (DateTimeException | IllegalArgumentException e) {
          row.valid = false;
          row.error = "第" + row.rowNumber + "行：时间格式、列数或字段长度无效";
          batch.invalidRows++;
        }
        rows.add(row);
      }
    } catch (IOException | UncheckedIOException e) {
      throw new ApiException(400, "INVALID_CSV", "无法解析UTF-8 CSV：" + e.getMessage());
    }
    batch.totalRows = rows.size();
    batch.rows = rows;
    return batch;
  }

  private ImportBatch savePreview(Long uid, ImportBatch batch) {
    batch.userId = uid;
    batch.status = "PREVIEW";
    Set<String> seen = new HashSet<>();
    for (ImportRow row : batch.rows) {
      if (row.valid) {
        row.dedupKey =
            dedup(
                LocalDateTime.parse(row.startTime),
                LocalDateTime.parse(row.endTime),
                row.sourceName);
        if (!seen.add(row.dedupKey) || mapper.duplicate(uid, row.dedupKey) != null) {
          row.error = "重复记录，确认时跳过";
          batch.duplicateRows++;
        }
      }
    }
    mapper.insertBatch(batch);
    for (ImportRow row : batch.rows) {
      row.batchId = batch.id;
      mapper.insertRow(row);
    }
    notifications.importIssues(uid, batch.id, false, batch.validRows, batch.invalidRows + batch.unsupportedRows);
    return batch;
  }

  private static LocalDateTime parseTime(String value, ZoneId zone, ZoneId profileZone) {
    var parsed =
        DateTimeFormatter.ISO_DATE_TIME.parseBest(value, OffsetDateTime::from, LocalDateTime::from);
    return parsed instanceof OffsetDateTime offset
        ? offset.atZoneSameInstant(profileZone).toLocalDateTime()
        : ((LocalDateTime) parsed).atZone(zone).withZoneSameInstant(profileZone).toLocalDateTime();
  }

  @Transactional
  public ImportBatch confirm(Long uid, Long id) {
    Identity.requireExisting(mapper.lockUser(uid));
    ImportBatch b = mapper.lockBatch(uid, id);
    if (b == null) throw ApiException.missing();
    if ("CONFIRMED".equals(b.status)) return b;
    b.duplicateRows = 0;
    Map<String, LocalDate> latestBySource = new HashMap<>();
    for (ImportRow row : mapper.rows(id))
      if (row.valid) {
        if (mapper.duplicate(uid, row.dedupKey) != null) {
          b.duplicateRows++;
          continue;
        }
        SleepRecord sleep =
            record(
                uid,
                new Requests.Sleep(
                    LocalDateTime.parse(row.startTime),
                    LocalDateTime.parse(row.endTime),
                    row.sourceName,
                    row.note));
        if (row.sleepDate != null) sleep.sleepDate = row.sleepDate;
        sleep.sourceApp = b.sourceApp;
        sleep.deviceBrand = b.deviceBrand;
        sleep.deviceType = b.deviceType;
        sleep.deviceModel = b.deviceModel;
        mapper.insert(sleep);
        latestBySource.merge(sleep.sourceName, sleep.sleepDate, (a, bDate) -> a.isAfter(bDate) ? a : bDate);
        b.insertedRows++;
      }
    b.status = "CONFIRMED";
    mapper.confirmBatch(b);
    importAdmin.snapshotIssues(Kind.SLEEP, id);
    mapper.deleteRows(id);
    triggers.sleepImported(uid, id, latestBySource);
    return b;
  }

  public List<ImportBatch> imports(Long uid) {
    return mapper.batches(uid);
  }

  private static SleepRecord record(Long uid, Requests.Sleep r) {
    SleepRecord s = new SleepRecord();
    s.userId = uid;
    s.startTime = r.startTime().truncatedTo(ChronoUnit.SECONDS);
    s.endTime = r.endTime().truncatedTo(ChronoUnit.SECONDS);
    s.sleepDate = s.endTime.toLocalDate();
    s.durationMinutes = ChronoUnit.SECONDS.between(s.startTime, s.endTime) / 60.0;
    s.sourceName = r.sourceName();
    s.note = r.note();
    s.dedupKey = dedup(s.startTime, s.endTime, s.sourceName);
    return s;
  }

  private static String dedup(LocalDateTime start, LocalDateTime end, String source) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (start.truncatedTo(ChronoUnit.SECONDS)
                              + "|"
                              + end.truncatedTo(ChronoUnit.SECONDS)
                              + "|"
                              + source)
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
