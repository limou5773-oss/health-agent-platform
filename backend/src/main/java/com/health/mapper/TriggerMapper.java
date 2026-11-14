package com.health.mapper;

import com.health.model.TriggerModels.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TriggerMapper {
  Settings settings(Long userId);
  void save(@Param("userId") Long userId, @Param("settings") Settings settings);
  List<Long> reviewUsers();
  List<Run> runs(Long userId);
  boolean exists(@Param("userId") Long userId, @Param("kind") Kind kind,
      @Param("key") String key, @Param("start") LocalDate start, @Param("end") LocalDate end);
  void insert(@Param("userId") Long userId, @Param("kind") Kind kind, @Param("key") String key,
      @Param("start") LocalDate start, @Param("end") LocalDate end, @Param("sourceName") String sourceName,
      @Param("taskId") String taskId);
  void notifyRun(@Param("userId") Long userId, @Param("kind") Kind kind, @Param("taskId") String taskId);
}
