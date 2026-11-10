package com.health.mapper;

import com.health.api.Requests.SleepQuery;
import com.health.model.ActivityModels.*;
import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ActivityMapper {
  Long lockUser(Long userId);
  List<StepRecord> steps(@Param("userId") Long uid, @Param("q") SleepQuery q, @Param("offset") long offset);
  long stepCount(@Param("userId") Long uid, @Param("q") SleepQuery q);
  StepRecord step(@Param("userId") Long uid, @Param("id") Long id);
  List<StepRecord> stepRange(@Param("userId") Long uid, @Param("start") LocalDate start, @Param("end") LocalDate end, @Param("source") String source);
  List<StepRecord> stepDay(@Param("userId") Long uid, @Param("date") LocalDate date, @Param("source") String source);
  void insertStep(StepRecord record);
  void updateStep(StepRecord record);
  void deleteStep(@Param("userId") Long uid, @Param("id") Long id);
  List<String> stepSources(Long userId);
  Integer goal(Long userId);
  void saveGoal(@Param("userId") Long uid, @Param("dailySteps") int steps);
  void insertBatch(StepImportBatch batch);
  void insertRow(StepImportRow row);
  List<StepImportBatch> batches(Long userId);
  StepImportBatch batch(@Param("userId") Long uid, @Param("id") Long id);
  List<StepImportRow> rows(Long batchId);
  void deleteRows(Long batchId);
  void confirmBatch(StepImportBatch batch);
  List<Workout> workouts(@Param("userId") Long uid, @Param("q") SleepQuery q, @Param("offset") long offset);
  long workoutCount(@Param("userId") Long uid, @Param("q") SleepQuery q);
  Workout workout(@Param("userId") Long uid, @Param("id") Long id);
  Workout workoutDuplicate(Workout record);
  List<Workout> workoutRange(@Param("userId") Long uid, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("source") String source);
  void insertWorkout(Workout record);
  void updateWorkout(Workout record);
  void deleteWorkout(@Param("userId") Long uid, @Param("id") Long id);
  List<String> workoutSources(Long userId);
  List<EligiblePlanItem> planItems(@Param("userId") Long uid, @Param("date") LocalDate date);
}
