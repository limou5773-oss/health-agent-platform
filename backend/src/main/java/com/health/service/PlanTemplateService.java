package com.health.service;

import com.health.api.PlanTemplateRequests.Save;
import com.health.api.PlanTemplateRequests.Use;
import com.health.model.Models.Task;
import com.health.model.PlanTemplateModels.Template;
import java.util.List;

public interface PlanTemplateService {
  List<Template> list(boolean admin);
  Template save(Long id, Save request);
  Task draft(Long userId, Long id, Use request);
}
