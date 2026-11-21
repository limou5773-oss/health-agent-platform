package com.health.api;

import java.util.List;

public final class MiFitnessSourceRequired extends ApiException {
  public final List<String> sourceIds;

  public MiFitnessSourceRequired(List<String> sourceIds) {
    super(409, "MI_FITNESS_SOURCE_REQUIRED", "文件包含多个设备来源，请选择本批要导入的设备");
    this.sourceIds = sourceIds;
  }
}
