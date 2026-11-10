package com.health.mapper;

import com.health.api.Requests.SleepQuery;
import com.health.model.WeightModels.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WeightMapper {
  Long lockUser(Long userId);
  List<WeightRecord> records(@Param("userId") Long uid, @Param("q") SleepQuery q, @Param("offset") long offset);
  long recordCount(@Param("userId") Long uid, @Param("q") SleepQuery q);
  WeightRecord record(@Param("userId") Long uid, @Param("id") Long id);
  List<WeightRecord> range(@Param("userId") Long uid, @Param("start") LocalDate start,
      @Param("end") LocalDate end, @Param("source") String source);
  void insertRecord(WeightRecord record);
  void updateRecord(WeightRecord record);
  void deleteRecord(@Param("userId") Long uid, @Param("id") Long id);
  List<String> sources(Long userId);
  WeightGoal goal(Long userId);
  void saveGoal(WeightGoal goal);
  void deleteGoal(Long userId);
}
