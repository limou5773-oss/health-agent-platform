package com.health.service;

import com.health.model.Models.*;
import java.time.LocalDate;
import java.util.*;

public interface PlanService {
  List<Plan> list(Long uid);

  Plan get(Long uid, Long id);

  Plan status(Long uid, Long id, String status);

  Map<String, Object> checkin(Long uid, Long id, LocalDate date);

  void uncheck(Long uid, Long id);

  List<Report> reports(Long uid);

  Report report(Long uid, Long id);
}
