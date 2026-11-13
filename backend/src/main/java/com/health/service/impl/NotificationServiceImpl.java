package com.health.service.impl;

import com.health.mapper.NotificationMapper;
import com.health.model.Models.Page;
import com.health.model.NotificationModels.*;
import com.health.service.NotificationService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {
  private final NotificationMapper mapper;

  public NotificationServiceImpl(NotificationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<Message> messages(Long userId, int page, int pageSize, boolean unreadOnly) {
    return new Page<>(mapper.messages(userId, (page - 1L) * pageSize, pageSize, unreadOnly),
        mapper.count(userId, unreadOnly), page, pageSize);
  }

  @Override
  public long unread(Long userId) {
    return mapper.count(userId, true);
  }

  @Override
  public void read(Long userId, Long id) {
    mapper.read(userId, id);
  }

  @Override
  public void readAll(Long userId) {
    mapper.readAll(userId);
  }

  @Override
  public Settings settings(Long userId) {
    Settings saved = mapper.settings(userId);
    return saved == null
        ? new Settings(false, CheckinFrequency.PER_TASK, LocalTime.of(20, 0), false, false, false, false)
        : saved;
  }

  @Override
  public Settings saveSettings(Long userId, Settings settings) {
    mapper.saveSettings(userId, settings);
    return settings;
  }

  @Override
  public void reportReady(Long userId, Long reportId) {
    mapper.reportReady(userId, reportId);
  }

  @Override
  public void importIssues(Long userId, Long batchId, boolean steps, int valid, int invalid) {
    if (invalid > 0) mapper.importIssues(userId, batchId, steps, valid, invalid);
  }

  @Override
  public void importRejected(Long userId, boolean steps) {
    mapper.importRejected(userId, steps);
  }

  @Scheduled(fixedDelay = 60_000)
  public void schedule() {
    Instant now = Instant.now();
    for (String zone : mapper.activeZones()) {
      mapper.schedule(zone, now.atZone(ZoneId.of(zone)).toLocalDateTime());
    }
  }
}
