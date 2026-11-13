package com.health.mapper;

import com.health.api.TimelineRequests.Query;
import com.health.model.TimelineModels.Event;
import com.health.model.TimelineModels.EventType;
import java.util.List;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.annotations.Param;

public interface TimelineMapper {
  List<Event> events(@Param("userId") Long userId, @Param("q") Query q, @Param("offset") long offset);

  long count(@Param("userId") Long userId, @Param("q") Query q);

  Cursor<Event> exportEvents(@Param("userId") Long userId, @Param("q") Query q);

  Event event(@Param("userId") Long userId, @Param("type") EventType type, @Param("id") Long id);
}
