package com.health.service;

import com.health.model.MeasurementImportModels.*;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface MeasurementImportService {
  Preview preview(Long userId, Metric metric, MultipartFile file);
  Preview previewApple(Long userId, Metric metric, MultipartFile file);
  Preview previewMiFitness(Long userId, Metric metric, MultipartFile file, String requestedSid);
  Preview preview(Long userId, Metric metric, Long id);
  Batch confirm(Long userId, Metric metric, Long id);
  List<Batch> history(Long userId, Metric metric);
}
