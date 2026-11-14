package com.health.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface MealPhotoService {
  Preview preview(Long userId, MultipartFile photo);

  record Candidate(String foodName, Integer portionGrams, String confidence) {}
  record Preview(List<Candidate> candidates) {}
}
