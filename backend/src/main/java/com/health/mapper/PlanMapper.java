package com.health.mapper;

import com.health.model.Models.*;
import com.health.api.HealthReportRequests.Generate;
import com.health.model.HealthReportModels.Completion;
import com.health.model.HealthReportModels.Source;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface PlanMapper {
  List<Plan> list(Long userId);

  List<AgentPlan> agentPlans(Long userId);

  Plan get(@Param("userId") Long userId, @Param("id") Long id);

  Plan lock(@Param("userId") Long userId, @Param("id") Long id);

  List<PlanItem> items(Long planId);

  void insert(Plan plan);

  void insertItem(PlanItem item);

  void status(@Param("id") Long id, @Param("status") String status);

  void insertStatusChange(
      @Param("planId") Long planId,
      @Param("previousStatus") String previousStatus,
      @Param("status") String status);

  PlanItem itemOwner(@Param("userId") Long userId, @Param("id") Long id);

  Map<String, Object> checkin(Long itemId);

  void insertCheckin(@Param("id") Long id, @Param("date") LocalDate date);

  int deleteCheckin(Long itemId);

  List<Report> reports(Long userId);

  Report report(@Param("userId") Long userId, @Param("id") Long id);

  void insertReport(Report report);

  int nextReportVersion(@Param("userId") Long userId, @Param("type") String type,
      @Param("start") LocalDate start, @Param("end") LocalDate end);

  Completion reportCompletion(@Param("userId") Long userId,
      @Param("start") LocalDate start, @Param("end") LocalDate end);

  List<Source> reportSources(@Param("userId") Long userId, @Param("start") LocalDate start,
      @Param("end") LocalDate end, @Param("request") Generate request);

  void invalidateHealthReports(@Param("userId") Long userId,
      @Param("start") LocalDate start, @Param("end") LocalDate end);
}
