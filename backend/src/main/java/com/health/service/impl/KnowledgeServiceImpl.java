package com.health.service.impl;

import com.health.api.*;
import com.health.mapper.KnowledgeMapper;
import com.health.model.Models.Article;
import com.health.service.KnowledgeService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {
  private final KnowledgeMapper mapper;

  public KnowledgeServiceImpl(KnowledgeMapper mapper) {
    this.mapper = mapper;
  }

  public List<Article> list(String query, boolean all) {
    return mapper.list(query, all);
  }

  public Article save(Long id, Requests.Article r) {
    Article a = new Article();
    a.id = id;
    a.title = r.title();
    a.content = r.content();
    a.sourceName = r.sourceName();
    a.sourceUrl = r.sourceUrl();
    a.published = r.published();
    if (id == null) mapper.insert(a);
    else if (mapper.update(a) == 0) throw ApiException.missing();
    return mapper.get(a.id);
  }
}
