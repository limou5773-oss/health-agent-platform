package com.health.mapper;

import com.health.model.Models.*;
import com.health.model.TaskAudit;
import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.annotations.Param;

public interface TaskMapper {
  Long lockUser(Long userId);
  boolean enabled(Long userId);

  Task get(String id);

  Task lock(String id);

  void insert(Task task);

  void update(Task task);
  TaskAudit.Metadata audit(String id);
  List<TaskAudit.Step> auditSteps(String id);
  void saveFeedback(@Param("id") String id, @Param("feedback") TaskAudit.Feedback feedback);
  void addDuration(@Param("id") String id, @Param("elapsedMs") long elapsedMs);

  void delete(String id);

  List<Task> list(Long userId);
  List<Task> descendants(@Param("userId") Long userId, @Param("id") String id);

  List<Map<String, Object>> admin();

  List<Step> steps(String id);

  void insertStep(Step step);

  void deleteSteps(String id);

  void detachPlans(String id);

  void detachReports(String id);

  List<Task> affected(
      @Param("userId") Long userId, @Param("date") LocalDate date, @Param("source") String source);

  void invalidateReports(
      @Param("userId") Long userId, @Param("date") LocalDate date, @Param("source") String source,
      @Param("change") ReportSourceChange change);

  List<Task> affectedDiary(@Param("userId") Long userId, @Param("dates") Set<LocalDate> dates);
  List<Task> affectedCombined(@Param("userId") Long userId, @Param("start") LocalDate start,
      @Param("end") LocalDate end);
  List<Task> affectedCheckins(@Param("userId") Long userId, @Param("date") LocalDate date);

  void invalidateDiaryReports(@Param("userId") Long userId, @Param("dates") Set<LocalDate> dates,
      @Param("change") ReportSourceChange change);

  void recoverInterrupted();
}
