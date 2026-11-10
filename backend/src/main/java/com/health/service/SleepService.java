package com.health.service;

import com.health.api.Requests;
import com.health.model.Models.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface SleepService {
  Page<SleepRecord> list(Long userId, Requests.SleepQuery q);

  SleepRecord create(Long userId, Requests.Sleep r);

  SleepRecord update(Long userId, Long id, Requests.Sleep r);

  void delete(Long userId, Long id);

  List<String> sources(Long userId);

  Statistics statistics(Long userId, LocalDate start, LocalDate end, String source);

  byte[] export(Long userId, Requests.SleepQuery q);

  ImportBatch preview(Long userId, MultipartFile file, String timezone);

  ImportBatch applePreview(Long userId, MultipartFile file);

  ImportBatch confirm(Long userId, Long id);

  List<ImportBatch> imports(Long userId);
}
