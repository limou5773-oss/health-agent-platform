package com.health.mapper;

import com.health.model.NotificationModels.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NotificationMapper {
  Settings settings(Long userId);
  void saveSettings(@Param("userId") Long userId, @Param("settings") Settings settings);

  List<Message> messages(@Param("userId") Long userId, @Param("offset") long offset,
      @Param("size") int size, @Param("unreadOnly") boolean unreadOnly);
  long count(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly);
  void read(@Param("userId") Long userId, @Param("id") Long id);
  void readAll(Long userId);

  List<String> activeZones();
  void schedule(@Param("zone") String zone, @Param("localNow") LocalDateTime localNow);
  void reportReady(@Param("userId") Long userId, @Param("reportId") Long reportId);
  void importIssues(@Param("userId") Long userId, @Param("batchId") Long batchId,
      @Param("steps") boolean steps, @Param("valid") int valid, @Param("invalid") int invalid);
  void importRejected(@Param("userId") Long userId, @Param("steps") boolean steps);
}
