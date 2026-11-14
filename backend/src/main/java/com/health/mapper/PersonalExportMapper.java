package com.health.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

public interface PersonalExportMapper {
  enum Section {
    PROFILE, PREFERENCES, SLEEP, STEPS, WORKOUTS, MEALS, WATER, WEIGHT, HEART_RATE, DIARY,
    ACTIVITY_GOAL, WEIGHT_GOAL, TASKS, TASK_STEPS, PLANS, PLAN_REVISIONS, PLAN_STATUS_CHANGES,
    PLAN_ITEMS, CHECKINS, REPORTS, SLEEP_IMPORTS, STEP_IMPORTS, MEASUREMENT_IMPORTS, NOTIFICATIONS,
    NOTIFICATION_SETTINGS, TRIGGER_SETTINGS, TRIGGER_RUNS, KNOWLEDGE_FAVORITES, REJECTED_IMPORTS
  }

  Cursor<Map<String, Object>> rows(@Param("userId") Long userId, @Param("section") Section section);
}
