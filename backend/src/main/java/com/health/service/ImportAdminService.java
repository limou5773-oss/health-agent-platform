package com.health.service;

import com.health.model.ImportAdminModels.*;
import com.health.model.Models.Page;
import java.util.List;

public interface ImportAdminService {
  Page<Batch> batches(Kind kind, boolean issuesOnly, int page, int size);
  List<Issue> issues(Kind kind, Long id);
  Template template(Kind kind);
  Template saveTemplate(Kind kind, String instructions);
  void snapshotIssues(Kind kind, Long id);
  void recordIssue(Kind kind, Long userId, Long id, String reason);
  void rejected(Kind kind, Long userId, String code);
}
