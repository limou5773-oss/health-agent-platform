package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.api.ApiException;
import com.health.api.TimelineRequests.Query;
import com.health.mapper.TimelineMapper;
import com.health.model.Models.Page;
import com.health.model.TimelineModels.Event;
import com.health.model.TimelineModels.EventType;
import com.health.service.TimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineServiceImpl implements TimelineService {
  private final TimelineMapper mapper;
  private final ObjectMapper json;

  public TimelineServiceImpl(TimelineMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public Page<Event> events(Long userId, Query q) {
    long offset = (q.page - 1L) * q.pageSize;
    return new Page<>(mapper.events(userId, q, offset), mapper.count(userId, q), q.page, q.pageSize);
  }

  public Event event(Long userId, EventType type, Long id) {
    Event event = mapper.event(userId, type, id);
    if (event == null) throw ApiException.missing();
    try {
      event.details = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .readTree(event.detailsJson);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("时间线事件详情 JSON 无效", e);
    }
    return event;
  }
}
