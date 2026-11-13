package com.health.mapper;

import com.health.model.PlanTemplateModels.Template;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PlanTemplateMapper {
  List<Template> list(boolean admin);
  Template lock(Long id);
  void insert(Template template);
  void update(@Param("template") Template template);
}
