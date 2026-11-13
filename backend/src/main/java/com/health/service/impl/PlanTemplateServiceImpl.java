package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.ApiException;
import com.health.api.PlanTemplateRequests.*;
import com.health.api.Requests.Draft;
import com.health.api.Requests.DraftItem;
import com.health.mapper.PlanTemplateMapper;
import com.health.model.Models.Task;
import com.health.model.PlanTemplateModels.Template;
import com.health.service.PlanTemplateService;
import com.health.service.TaskService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanTemplateServiceImpl implements PlanTemplateService {
  private final PlanTemplateMapper mapper;
  private final TaskService tasks;
  private final ObjectMapper json;

  public PlanTemplateServiceImpl(PlanTemplateMapper mapper, TaskService tasks, ObjectMapper json) {
    this.mapper = mapper;
    this.tasks = tasks;
    this.json = json;
  }

  @Override
  public List<Template> list(boolean admin) {
    return mapper.list(admin).stream().map(this::read).toList();
  }

  @Override
  @Transactional
  public Template save(Long id, Save request) {
    Template template = id == null ? new Template() : load(id);
    if (template.version != request.version()) throw ApiException.conflict("模板版本已变化，请刷新后重试");
    template.category = request.category();
    template.title = request.title();
    template.instructions = request.instructions();
    template.days = request.days();
    template.enabled = request.enabled();
    template.itemsJson = json.valueToTree(request.items()).toString();
    template.version++;
    if (id == null) mapper.insert(template);
    else mapper.update(template);
    return read(template);
  }

  @Override
  @Transactional
  public Task draft(Long userId, Long id, Use request) {
    Template template = load(id);
    if (!template.enabled || template.version != request.version())
      throw ApiException.conflict("模板已更新或停用，请刷新后重新选择");
    read(template);
    var start = request.startDate();
    var items = template.items.stream().map(item -> new DraftItem(
        start.plusDays(item.day() - 1L), item.time(), item.title(), item.description())).toList();
    var draft = new Draft(1, template.title, template.instructions, start, start.plusDays(template.days - 1L), items);
    return tasks.createTemplateDraft(userId, template, draft);
  }

  private Template load(Long id) {
    Template template = mapper.lock(id);
    if (template == null) throw ApiException.missing();
    return template;
  }

  private Template read(Template template) {
    try {
      template.items = json.readValue(template.itemsJson, new TypeReference<>() {});
      return template;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("已保存的模板任务无法读取", e);
    }
  }
}
