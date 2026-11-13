package com.health.mapper;

import com.health.api.Requests.StatisticsQuery;
import com.health.model.HeartRateModels.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface HeartRateMapper {
  List<Sample> records(@Param("userId") Long userId, @Param("q") StatisticsQuery query, @Param("offset") long offset);
  Totals totals(@Param("userId") Long userId, @Param("q") StatisticsQuery query);
  List<Daily> daily(@Param("userId") Long userId, @Param("q") StatisticsQuery query);
  boolean hasOverlappingSources(@Param("userId") Long userId, @Param("q") StatisticsQuery query);
  List<String> sources(Long userId);
  Sample record(@Param("userId") Long userId, @Param("id") Long id);
  void insert(Sample sample);
  void update(Sample sample);
  int delete(@Param("userId") Long userId, @Param("id") Long id);
}
