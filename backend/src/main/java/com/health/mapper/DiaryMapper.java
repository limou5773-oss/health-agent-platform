package com.health.mapper;

import com.health.api.DiaryRequests;
import com.health.model.DiaryModels.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DiaryMapper {
  Long lockUser(Long userId);
  List<DiaryRecord> entries(@Param("userId") Long uid, @Param("q") DiaryRequests.Query q, @Param("offset") long offset);
  long count(@Param("userId") Long uid, @Param("q") DiaryRequests.Query q);
  DiaryRecord entry(@Param("userId") Long uid, @Param("id") Long id);
  void insert(DiaryRecord record);
  void update(DiaryRecord record);
  void permission(@Param("userId") Long uid, @Param("id") Long id, @Param("allowed") boolean allowed);
  void delete(@Param("userId") Long uid, @Param("id") Long id);
  List<String> sources(Long userId);
  List<DiaryEntry> authorized(@Param("userId") Long uid, @Param("start") LocalDate start, @Param("end") LocalDate end);
  long authorizedCount(@Param("userId") Long uid, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
