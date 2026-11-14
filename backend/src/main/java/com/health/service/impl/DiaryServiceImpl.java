package com.health.service.impl;

import com.health.security.Identity;
import com.health.api.ApiException;
import com.health.api.DiaryRequests;
import com.health.mapper.DiaryMapper;
import com.health.model.DiaryModels.*;
import com.health.model.Models.Page;
import com.health.model.Models.ReportSourceChange;
import com.health.service.DiaryService;
import com.health.service.TaskService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryServiceImpl implements DiaryService {
  private final DiaryMapper mapper;
  private final TaskService tasks;

  public DiaryServiceImpl(DiaryMapper mapper, TaskService tasks) {
    this.mapper = mapper;
    this.tasks = tasks;
  }

  public Page<DiaryRecord> entries(Long uid, DiaryRequests.Query q) {
    return new Page<>(mapper.entries(uid, q, (q.page - 1L) * q.pageSize), mapper.count(uid, q), q.page, q.pageSize);
  }

  public DiaryRecord entry(Long uid, Long id) {
    DiaryRecord record = mapper.entry(uid, id);
    if (record == null) throw ApiException.missing();
    return record;
  }

  @Transactional
  public DiaryRecord create(Long uid, DiaryRequests.Entry request) {
    Identity.requireExisting(mapper.lockUser(uid));
    DiaryRecord record = record(uid, request);
    mapper.insert(record);
    if (record.agentAllowed) tasks.invalidateDiary(uid, Set.of(record.recordedAt.toLocalDate()), ReportSourceChange.DIARY_CHANGED);
    return mapper.entry(uid, record.id);
  }

  @Transactional
  public DiaryRecord update(Long uid, Long id, DiaryRequests.Entry request) {
    Identity.requireExisting(mapper.lockUser(uid));
    DiaryRecord old = entry(uid, id);
    DiaryRecord record = record(uid, request);
    record.id = id;
    mapper.update(record);
    Set<LocalDate> dates = new HashSet<>();
    if (old.agentAllowed) dates.add(old.recordedAt.toLocalDate());
    if (record.agentAllowed) dates.add(record.recordedAt.toLocalDate());
    if (!dates.isEmpty()) tasks.invalidateDiary(uid, dates, ReportSourceChange.DIARY_CHANGED);
    return mapper.entry(uid, id);
  }

  @Transactional
  public DiaryRecord permission(Long uid, Long id, boolean allowed) {
    Identity.requireExisting(mapper.lockUser(uid));
    DiaryRecord record = entry(uid, id);
    if (record.agentAllowed == allowed) return record;
    mapper.permission(uid, id, allowed);
    tasks.invalidateDiary(uid, Set.of(record.recordedAt.toLocalDate()), ReportSourceChange.DIARY_CHANGED);
    return mapper.entry(uid, id);
  }

  @Transactional
  public void delete(Long uid, Long id) {
    Identity.requireExisting(mapper.lockUser(uid));
    DiaryRecord record = entry(uid, id);
    mapper.delete(uid, id);
    if (record.agentAllowed) tasks.invalidateDiary(uid, Set.of(record.recordedAt.toLocalDate()), ReportSourceChange.DELETED);
  }

  public List<String> sources(Long uid) { return mapper.sources(uid); }

  @Transactional(readOnly = true)
  public DiaryContext authorized(Long uid, LocalDate start, LocalDate end) {
    long total = mapper.authorizedCount(uid, start, end);
    List<DiaryEntry> entries = mapper.authorized(uid, start, end);
    return new DiaryContext(start, end, 20, total, entries.size(), total > entries.size(), entries);
  }

  private static DiaryRecord record(Long uid, DiaryRequests.Entry request) {
    DiaryRecord record = new DiaryRecord();
    record.userId = uid;
    record.recordedAt = request.recordedAt();
    record.mood = request.mood();
    record.stressLevel = request.stressLevel() == null ? null : request.stressLevel().intValueExact();
    record.bodyFeeling = request.bodyFeeling();
    record.events = request.events();
    record.tags = request.tags();
    record.note = request.note();
    record.sourceName = request.sourceName();
    record.agentAllowed = request.agentAllowed();
    return record;
  }
}
