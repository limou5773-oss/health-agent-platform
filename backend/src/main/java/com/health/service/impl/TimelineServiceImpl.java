package com.health.service.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.health.api.ApiException;
import com.health.api.TimelineRequests.Query;
import com.health.mapper.TimelineMapper;
import com.health.model.Models.Page;
import com.health.model.TimelineModels.Event;
import com.health.model.TimelineModels.EventType;
import com.health.service.TimelineService;
import java.io.IOException;
import java.io.OutputStream;
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
    return readDetails(event);
  }

  @Override
  @Transactional(readOnly = true)
  public void exportEvents(Long userId, Query q, OutputStream output) throws IOException {
    try (var events = mapper.exportEvents(userId, q);
         JsonGenerator generator = json.getFactory().createGenerator(output)) {
      generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
      generator.disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT);
      var writer = json.writer().without(SerializationFeature.FLUSH_AFTER_WRITE_VALUE);
      generator.writeStartObject();
      generator.writeStringField("startDate", q.startDate.toString());
      generator.writeStringField("endDate", q.endDate.toString());
      generator.writeStringField("type", q.getType() == null ? null : q.getType().name());
      generator.writeStringField("sourceName", q.sourceName);
      generator.writeArrayFieldStart("records");
      for (Event event : events) writer.writeValue(generator, readDetails(event));
      generator.writeEndArray();
      generator.writeEndObject();
    }
  }

  private Event readDetails(Event event) {
    try {
      event.details = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .readTree(event.detailsJson);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("时间线事件详情 JSON 无效", e);
    }
    return event;
  }
}
