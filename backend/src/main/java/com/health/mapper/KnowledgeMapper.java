package com.health.mapper;

import com.health.model.Models.Article;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface KnowledgeMapper {
  List<Article> list(@Param("query") String query, @Param("all") boolean all);

  Article get(Long id);

  void insert(Article article);

  int update(Article article);
}
