package com.health.service.impl;

import com.health.api.ApiException;
import com.health.service.MealPhotoService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MealPhotoServiceImpl implements MealPhotoService {
  private final ImageRecognitionService images;

  public MealPhotoServiceImpl(ImageRecognitionService images) {
    this.images = images;
  }

  @Override
  public Preview preview(Long userId, MultipartFile photo) {
    Preview result = images.recognize(userId, photo, "/internal/model/meal-photo", Preview.class);
    if (result.candidates() == null)
      throw new ApiException(502, "INVALID_AGENT_RESULT", "未收到有效识别结果，请手动录入");
    return result;
  }
}
