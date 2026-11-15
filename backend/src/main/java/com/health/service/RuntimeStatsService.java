package com.health.service;

import com.health.model.RuntimeStats;
import com.health.model.RuntimeStats.TaskRow;
import com.health.model.RuntimeStats.TaskStatus;
import com.health.model.Models.Page;
import java.time.LocalDate;

public interface RuntimeStatsService {
  Page<TaskRow> tasks(LocalDate start, LocalDate end, TaskStatus status, int page);
  RuntimeStats stats(LocalDate start, LocalDate end);
}
