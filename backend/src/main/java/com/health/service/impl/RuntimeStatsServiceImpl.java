package com.health.service.impl;

import com.health.mapper.RuntimeStatsMapper;
import com.health.model.RuntimeStats;
import com.health.model.RuntimeStats.StatusCount;
import com.health.service.RuntimeStatsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeStatsServiceImpl implements RuntimeStatsService {
  private final RuntimeStatsMapper mapper;

  public RuntimeStatsServiceImpl(RuntimeStatsMapper mapper) { this.mapper = mapper; }

  @Override
  @Transactional(readOnly = true)
  public RuntimeStats stats(LocalDate start, LocalDate end) {
    LocalDate until = end.plusDays(1);
    var statuses = mapper.statuses(start, until);
    long completed = statuses.stream().filter(row -> "COMPLETED".equals(row.status()))
        .mapToLong(StatusCount::count).sum();
    long terminal = statuses.stream().filter(row -> switch (row.status()) {
      case "COMPLETED", "FAILED", "CANCELLED" -> true;
      default -> false;
    }).mapToLong(StatusCount::count).sum();
    var timing = mapper.timing(start, until);
    var completion = mapper.completion(start, until);
    return new RuntimeStats(start, end, statuses, timing.timedTasks(), timing.averageDurationMs(),
        percent(completed, terminal), mapper.toolFailures(start, until), mapper.imports(start, until),
        completion.scheduled(), completion.completed(), percent(completion.completed(), completion.scheduled()));
  }

  private static BigDecimal percent(long count, long total) {
    return total == 0 ? null : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
  }
}
