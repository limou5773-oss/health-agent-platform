package com.health.mapper;

import java.util.List;

public interface AccountDeletionMapper {
  List<String> taskIds(Long userId);

  void deleteCheckins(Long userId);
  void deletePlanItems(Long userId);
  void deletePlanStatusChanges(Long userId);
  void deletePlanRevisions(Long userId);
  void deleteSleepImportRows(Long userId);
  void deleteStepImportRows(Long userId);
  void deleteMeasurementImportRows(Long userId);
  void deleteSleepImportIssues(Long userId);
  void deleteStepImportIssues(Long userId);
  void deleteMeasurementImportIssues(Long userId);
  void deleteNotificationSettings(Long userId);
  void deleteAgentTriggerSettings(Long userId);
  void deleteAgentTriggerRuns(Long userId);
  void deleteNotifications(Long userId);
  void deleteUserPreferences(Long userId);
  void deleteKnowledgeFavorites(Long userId);
  void deleteRejectedImports(Long userId);
  void deleteSleepRecords(Long userId);
  void deleteStepRecords(Long userId);
  void deleteWorkoutRecords(Long userId);
  void deleteMealRecords(Long userId);
  void deleteWaterRecords(Long userId);
  void deleteHeartRateSamples(Long userId);
  void deleteWeightRecords(Long userId);
  void deleteDiaryEntries(Long userId);
  void deleteCheckupReports(Long userId);
  void deleteActivityGoals(Long userId);
  void deleteWeightGoals(Long userId);
  void deleteImportBatches(Long userId);
  void deleteStepImportBatches(Long userId);
  void deleteMeasurementImportBatches(Long userId);
  void deletePlans(Long userId);
  void deleteReports(Long userId);
  void deleteProfiles(Long userId);
  void deleteUsers(Long userId);
}
