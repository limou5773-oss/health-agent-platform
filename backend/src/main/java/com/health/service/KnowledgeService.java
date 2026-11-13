package com.health.service;

import com.health.api.Requests;
import com.health.model.Models.Article;
import com.health.model.KnowledgeOptions.*;
import java.util.List;

public interface KnowledgeService {
  List<Article> list(String query, boolean all);
  List<Article> browse(Long userId, String query, Category category, Sort sort, boolean favoritesOnly);
  Article published(Long userId, Long id);
  void favorite(Long userId, Long id);
  void unfavorite(Long userId, Long id);

  Article save(Long id, Requests.Article request);
}
