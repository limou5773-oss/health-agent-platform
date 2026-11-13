package com.health.mapper;

import com.health.model.MeasurementImportModels.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MeasurementImportMapper {
  void insertBatch(Batch batch);
  void insertRows(@Param("batchId") Long batchId, @Param("rows") List<Row> rows);
  Batch batch(@Param("userId") Long userId, @Param("metric") Metric metric, @Param("id") Long id);
  List<Batch> history(@Param("userId") Long userId, @Param("metric") Metric metric);
  List<String> sources(Long batchId);
  Long conflictRow(Batch batch);
  int existingRows(Batch batch);
  void updateBatch(Batch batch);
  int insertMeasurements(Batch batch);
  void confirm(Batch batch);
  void deleteRows(Long batchId);
}
