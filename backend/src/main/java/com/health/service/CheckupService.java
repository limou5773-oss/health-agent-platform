package com.health.service;

import com.health.model.CheckupModels.*;
import com.health.model.Models.Page;
import org.springframework.web.multipart.MultipartFile;

public interface CheckupService {
  Preview preview(Long userId, MultipartFile photo);
  Page<Report> list(Long userId, int page);
  Report get(Long userId, Long id);
  Report save(Long userId, Draft draft);
  void delete(Long userId, Long id);
}
