package com.health.service;

import com.health.api.TimelineRequests.Query;
import com.health.model.Models.Page;
import com.health.model.TimelineModels.Event;
import com.health.model.TimelineModels.EventType;
import java.io.IOException;
import java.io.OutputStream;

public interface TimelineService {
  Page<Event> events(Long userId, Query q);

  Event event(Long userId, EventType type, Long id);
  void exportEvents(Long userId, Query q, OutputStream output) throws IOException;
}
