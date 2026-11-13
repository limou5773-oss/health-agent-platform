package com.health.mapper;

import com.health.model.Models.*;
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

  void delete(String id);

  List<Task> list(Long userId);

  List<Map<String, Object>> admin();

  List<Step> steps(String id);

  void insertStep(Step step);

  void deleteSteps(String id);

  void detachPlans(String id);

  void detachReports(String id);

  List<Task> affected(
      @Param("userId") Long userId, @Param("date") LocalDate date, @Param("source") String source);

  void invalidateReports(
      @Param("userId") Long userId, @Param("date") LocalDate date, @Param("source") String source);

  List<Task> affectedDiary(@Param("userId") Long userId, @Param("dates") Set<LocalDate> dates);

  void invalidateDiaryReports(@Param("userId") Long userId, @Param("dates") Set<LocalDate> dates);

  void recoverInterrupted();
}
