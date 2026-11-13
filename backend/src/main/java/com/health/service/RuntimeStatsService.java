package com.health.service;

import com.health.model.RuntimeStats;
import java.time.LocalDate;

public interface RuntimeStatsService {
  RuntimeStats stats(LocalDate start, LocalDate end);
}
