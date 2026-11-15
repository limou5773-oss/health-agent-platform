package com.health.api;

import com.health.model.RuntimeStats;
import com.health.model.RuntimeStats.TaskRow;
import com.health.model.RuntimeStats.TaskStatus;
import com.health.model.Models.Page;
import com.health.service.RuntimeStatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/runtime-stats")
public class RuntimeStatsController {
  private final RuntimeStatsService service;

  public RuntimeStatsController(RuntimeStatsService service) { this.service = service; }

  @GetMapping
  public RuntimeStats stats(@Valid @ModelAttribute Query query) {
    return service.stats(query.startDate(), query.endDate());
  }

  @GetMapping("/tasks")
  public Page<TaskRow> tasks(@Valid @ModelAttribute Query query, @RequestParam TaskStatus status,
      @RequestParam(defaultValue = "1") @Min(1) int page) {
    return service.tasks(query.startDate(), query.endDate(), status, page);
  }

  public record Query(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    @AssertTrue(message = "统计周期须为1至366天")
    public boolean isRangeValid() {
      if (startDate == null || endDate == null) return true;
      long days = ChronoUnit.DAYS.between(startDate, endDate);
      return days >= 0 && days < 366;
    }
  }
}
