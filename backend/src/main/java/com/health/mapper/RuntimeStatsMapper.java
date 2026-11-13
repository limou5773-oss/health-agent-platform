package com.health.mapper;

import com.health.model.RuntimeStats.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RuntimeStatsMapper {
  List<StatusCount> statuses(@Param("start") LocalDate start, @Param("until") LocalDate until);
  Timing timing(@Param("start") LocalDate start, @Param("until") LocalDate until);
  List<ToolFailure> toolFailures(@Param("start") LocalDate start, @Param("until") LocalDate until);
  List<ImportVolume> imports(@Param("start") LocalDate start, @Param("until") LocalDate until);
  Completion completion(@Param("start") LocalDate start, @Param("until") LocalDate until);
}
