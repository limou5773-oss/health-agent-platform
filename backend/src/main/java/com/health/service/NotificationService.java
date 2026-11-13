package com.health.service;

import com.health.model.Models.Page;
import com.health.model.NotificationModels.*;

public interface NotificationService {
  Page<Message> messages(Long userId, int page, int pageSize, boolean unreadOnly);
  long unread(Long userId);
  void read(Long userId, Long id);
  void readAll(Long userId);
  Settings settings(Long userId);
  Settings saveSettings(Long userId, Settings settings);
  void reportReady(Long userId, Long reportId);
  void importIssues(Long userId, Long batchId, boolean steps, int valid, int invalid);
  void importRejected(Long userId, boolean steps);
}
