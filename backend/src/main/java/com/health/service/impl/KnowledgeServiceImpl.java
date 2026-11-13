package com.health.service.impl;

import com.health.api.*;
import com.health.mapper.KnowledgeMapper;
import com.health.model.Models.Article;
import com.health.model.KnowledgeOptions.*;
import com.health.service.KnowledgeService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {
  private final KnowledgeMapper mapper;

  public KnowledgeServiceImpl(KnowledgeMapper mapper) {
    this.mapper = mapper;
  }

  public List<Article> list(String query, boolean all) {
    return mapper.list(query, all, null, null, Sort.RELEVANCE, false);
  }

  public List<Article> browse(Long userId, String query, Category category, Sort sort, boolean favoritesOnly) {
    return mapper.list(query == null ? null : query.strip(), false, userId, category, sort, favoritesOnly);
  }

  public Article published(Long userId, Long id) {
    Article article = mapper.published(id, userId);
    if (article == null) throw ApiException.missing();
    return article;
  }

  @Transactional
  public void favorite(Long userId, Long id) {
    published(userId, id);
    mapper.favorite(userId, id);
  }

  public void unfavorite(Long userId, Long id) {
    mapper.unfavorite(userId, id);
  }

  public Article save(Long id, Requests.Article r) {
    Article a = new Article();
    a.id = id;
    a.title = r.title();
    a.content = r.content();
    a.sourceName = r.sourceName();
    a.sourceUrl = r.sourceUrl();
    a.published = r.published();
    a.category = r.category();
    if (id == null) mapper.insert(a);
    else if (mapper.update(a) == 0) throw ApiException.missing();
    return mapper.get(a.id);
  }
}
