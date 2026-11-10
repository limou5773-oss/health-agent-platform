package com.health.mapper;

import com.health.api.Requests.SleepQuery;
import com.health.model.Models.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SleepMapper {
  Long lockUser(Long userId);

  List<SleepRecord> list(
      @Param("userId") Long userId, @Param("q") SleepQuery q, @Param("offset") int offset);

  long count(@Param("userId") Long userId, @Param("q") SleepQuery q);

  List<SleepRecord> range(
      @Param("userId") Long userId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("source") String source);

  SleepRecord get(@Param("userId") Long userId, @Param("id") Long id);

  SleepRecord duplicate(@Param("userId") Long userId, @Param("key") String key);

  void insert(SleepRecord record);

  int update(SleepRecord record);

  int delete(@Param("userId") Long userId, @Param("id") Long id);

  List<String> sources(Long userId);

  void insertBatch(ImportBatch batch);

  void insertRow(ImportRow row);

  List<ImportBatch> batches(Long userId);

  ImportBatch lockBatch(@Param("userId") Long userId, @Param("id") Long id);

  List<ImportRow> rows(Long batchId);

  void deleteRows(Long batchId);

  void confirmBatch(ImportBatch batch);
}
