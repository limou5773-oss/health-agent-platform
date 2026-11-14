package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.ApiException;
import com.health.mapper.AccountMapper;
import com.health.mapper.CheckupMapper;
import com.health.model.CheckupModels.*;
import com.health.model.Models.Page;
import com.health.security.Identity;
import jakarta.validation.Validator;
import com.health.service.CheckupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CheckupServiceImpl implements CheckupService {
  private final CheckupMapper mapper;
  private final AccountMapper accounts;
  private final ImageRecognitionService images;
  private final ObjectMapper json;
  private final Validator validator;

  public CheckupServiceImpl(CheckupMapper mapper, AccountMapper accounts, ImageRecognitionService images,
      ObjectMapper json, Validator validator) {
    this.mapper = mapper;
    this.accounts = accounts;
    this.images = images;
    this.json = json;
    this.validator = validator;
  }

  public Preview preview(Long userId, MultipartFile photo) {
    Preview result = images.recognize(userId, photo, "/internal/model/checkup-photo", Preview.class);
    if (result.items() == null || result.items().size() > 50
        || result.items().stream().anyMatch(item -> item == null || !validator.validate(item).isEmpty()))
      throw new ApiException(502, "INVALID_AGENT_RESULT", "体检报告提取结果格式无效");
    return result;
  }

  @Transactional(readOnly = true)
  public Page<Report> list(Long userId, int page) {
    return new Page<>(mapper.list(userId, (page - 1L) * 20), mapper.count(userId), page, 20);
  }

  public Report get(Long userId, Long id) {
    Report report = mapper.get(userId, id);
    if (report == null) throw ApiException.missing();
    try {
      report.items = json.readValue(report.itemsJson, new TypeReference<>() {});
      return report;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("体检报告条目无法读取", e);
    }
  }

  @Transactional
  public Report save(Long userId, Draft draft) {
    Identity.requireExisting(accounts.lockUser(userId));
    Report report = new Report();
    report.userId = userId;
    report.reportDate = draft.reportDate();
    report.title = draft.title();
    report.items = draft.items();
    report.itemsJson = json.valueToTree(draft.items()).toString();
    mapper.insert(report);
    return report;
  }

  @Transactional
  public void delete(Long userId, Long id) {
    Identity.requireExisting(accounts.lockUser(userId));
    if (mapper.delete(userId, id) == 0) throw ApiException.missing();
  }
}
