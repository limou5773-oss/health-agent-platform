package com.health.service;

import com.health.api.TimelineRequests.ConfirmDelete;
import com.health.api.TimelineRequests.DeleteQuery;
import com.health.model.TimelineModels.DeletionPreview;
import java.io.IOException;

public interface HealthRecordDeletionService {
  DeletionPreview preview(Long userId, DeleteQuery query) throws IOException;
  int delete(Long userId, ConfirmDelete query) throws IOException;
}
