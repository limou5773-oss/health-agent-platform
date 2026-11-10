package com.health.service;

import com.health.api.Requests;
import com.health.model.Models.Article;
import java.util.List;

public interface KnowledgeService {
  List<Article> list(String query, boolean all);

  Article save(Long id, Requests.Article request);
}
