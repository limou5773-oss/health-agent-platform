package com.health.service.impl;

import com.health.api.ApiException;
import com.health.mapper.AccountDeletionMapper;
import com.health.mapper.AccountMapper;
import com.health.security.Identity;
import com.health.service.AccountDeletionService;
import com.health.service.TaskService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {
  private final AccountMapper accounts;
  private final AccountDeletionMapper data;
  private final TaskService tasks;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  public AccountDeletionServiceImpl(AccountMapper accounts, AccountDeletionMapper data, TaskService tasks) {
    this.accounts = accounts;
    this.data = data;
    this.tasks = tasks;
  }

  @Transactional
  public void delete(Long userId, String password) {
    Identity.requireExisting(accounts.lockUser(userId));
    var account = accounts.byId(userId);
    if ("ADMIN".equals(account.role))
      throw new ApiException(403, "ADMIN_DELETION_UNAVAILABLE", "系统管理员账号不能自助删除");
    if (!bcrypt.matches(password, account.passwordHash))
      throw new ApiException(400, "WRONG_PASSWORD", "当前密码错误");

    tasks.deleteForAccount(userId, data.taskIds(userId));
    data.deleteCheckins(userId);
    data.deletePlanItems(userId);
    data.deletePlanStatusChanges(userId);
    data.deletePlanRevisions(userId);
    data.deleteSleepImportRows(userId);
    data.deleteStepImportRows(userId);
    data.deleteMeasurementImportRows(userId);
    data.deleteSleepImportIssues(userId);
    data.deleteStepImportIssues(userId);
    data.deleteMeasurementImportIssues(userId);
    data.deleteNotificationSettings(userId);
    data.deleteAgentTriggerSettings(userId);
    data.deleteAgentTriggerRuns(userId);
    data.deleteNotifications(userId);
    data.deleteUserPreferences(userId);
    data.deleteKnowledgeFavorites(userId);
    data.deleteRejectedImports(userId);
    data.deleteSleepRecords(userId);
    data.deleteStepRecords(userId);
    data.deleteWorkoutRecords(userId);
    data.deleteMealRecords(userId);
    data.deleteWaterRecords(userId);
    data.deleteHeartRateSamples(userId);
    data.deleteWeightRecords(userId);
    data.deleteDiaryEntries(userId);
    data.deleteCheckupReports(userId);
    data.deleteFamilyGrants(userId);
    data.deleteActivityGoals(userId);
    data.deleteWeightGoals(userId);
    data.deleteImportBatches(userId);
    data.deleteStepImportBatches(userId);
    data.deleteMeasurementImportBatches(userId);
    data.deletePlans(userId);
    data.deleteReports(userId);
    data.deleteProfiles(userId);
    data.deleteUsers(userId);
  }
}
