package com.health.service.impl;

import com.health.api.ApiException;
import com.health.mapper.ImportAdminMapper;
import com.health.model.ImportAdminModels.*;
import com.health.model.Models.Page;
import com.health.service.ImportAdminService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportAdminServiceImpl implements ImportAdminService {
  private final ImportAdminMapper mapper;

  public ImportAdminServiceImpl(ImportAdminMapper mapper) { this.mapper = mapper; }

  @Transactional(readOnly = true)
  public Page<Batch> batches(Kind kind, boolean issuesOnly, int page, int size) {
    return new Page<>(mapper.batches(kind, issuesOnly, (page - 1L) * size, size),
        mapper.count(kind, issuesOnly), page, size);
  }

  @Transactional(readOnly = true)
  public List<Issue> issues(Kind kind, Long id) {
    Batch batch = mapper.batch(kind, id);
    if (batch == null) throw ApiException.missing();
    return mapper.issues(kind, id, "CONFIRMED".equals(batch.status()));
  }

  public Template template(Kind kind) {
    String saved = mapper.instructions(kind);
    return new Template(kind, kind.headers, saved == null ? "" : saved);
  }

  public Template saveTemplate(Kind kind, String instructions) {
    String value = instructions.strip();
    mapper.saveInstructions(kind, value);
    return new Template(kind, kind.headers, value);
  }

  public void snapshotIssues(Kind kind, Long id) { mapper.snapshotIssues(kind, id); }

  public void recordIssue(Kind kind, Long id, String reason) { mapper.recordIssue(kind, id, reason); }

  public void rejected(Kind kind, Long userId, String code) { mapper.rejected(kind, userId, code); }
}
