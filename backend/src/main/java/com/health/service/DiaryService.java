package com.health.service;

import com.health.api.DiaryRequests;
import com.health.model.DiaryModels.*;
import com.health.model.Models.Page;
import java.time.LocalDate;
import java.util.List;

public interface DiaryService {
  Page<DiaryRecord> entries(Long uid, DiaryRequests.Query query);
  DiaryRecord entry(Long uid, Long id);
  DiaryRecord create(Long uid, DiaryRequests.Entry request);
  DiaryRecord update(Long uid, Long id, DiaryRequests.Entry request);
  DiaryRecord permission(Long uid, Long id, boolean allowed);
  void delete(Long uid, Long id);
  List<String> sources(Long uid);
  DiaryContext authorized(Long uid, LocalDate start, LocalDate end);
}
