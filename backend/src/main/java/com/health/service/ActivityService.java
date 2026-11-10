package com.health.service;

import com.health.api.ActivityRequests;
import com.health.api.Requests.SleepQuery;
import com.health.model.ActivityModels.*;
import com.health.model.Models.Page;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ActivityService {
  Page<StepRecord> steps(Long uid, SleepQuery q);
  StepRecord createStep(Long uid, ActivityRequests.Step request);
  StepRecord updateStep(Long uid, Long id, ActivityRequests.Step request);
  void deleteStep(Long uid, Long id);
  List<String> stepSources(Long uid);
  StepStatistics stepStatistics(Long uid, LocalDate start, LocalDate end, String source);
  Goal goal(Long uid);
  Goal updateGoal(Long uid, ActivityRequests.Goal request);
  StepImportBatch preview(Long uid, MultipartFile file, String timezone);
  StepImportBatch confirm(Long uid, Long id);
  List<StepImportBatch> imports(Long uid);
  Page<Workout> workouts(Long uid, SleepQuery q);
  Workout createWorkout(Long uid, ActivityRequests.Workout request);
  Workout updateWorkout(Long uid, Long id, ActivityRequests.Workout request);
  void deleteWorkout(Long uid, Long id);
  List<String> workoutSources(Long uid);
  WorkoutStatistics workoutStatistics(Long uid, LocalDate start, LocalDate end, String source);
  List<EligiblePlanItem> planItems(Long uid, LocalDate date);
}
