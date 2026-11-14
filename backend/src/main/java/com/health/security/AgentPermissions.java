package com.health.security;

import com.health.api.ApiException;
import com.health.model.Models.Profile;
import com.health.model.Models.Task;

public final class AgentPermissions {
  private AgentPermissions() {}

  public static void require(Profile profile, String kind, boolean includeDiary) {
    if (!profile.aiConsent)
      throw new ApiException(403, "AI_CONSENT_REQUIRED", "请先在个人档案中授权AI分析");
    if ("COMBINED".equals(kind) ? !profile.canAnalyzeCombined() : !profile.aiSleep)
      throw new ApiException(403, "AI_METRIC_NOT_ALLOWED", "请先在个人档案中授权该任务所需的指标");
    if (includeDiary && !profile.aiDiary)
      throw new ApiException(403, "AI_DIARY_NOT_ALLOWED", "请先在个人档案中开启日记分析授权");
  }

  public static void requireCurrent(Profile profile, Task task) {
    if (task.aiConsentVersion != profile.aiConsentVersion)
      throw new ApiException(403, "AI_CONSENT_CHANGED", "此任务的授权已撤回，请按当前授权新建任务");
    require(profile, task.kind, task.includeDiary);
  }
}
