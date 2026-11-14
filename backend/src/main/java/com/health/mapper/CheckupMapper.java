package com.health.mapper;

import com.health.model.CheckupModels.Report;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CheckupMapper {
  List<Report> list(@Param("userId") Long userId, @Param("offset") long offset);
  long count(Long userId);
  Report get(@Param("userId") Long userId, @Param("id") Long id);
  void insert(Report report);
  int delete(@Param("userId") Long userId, @Param("id") Long id);
}
