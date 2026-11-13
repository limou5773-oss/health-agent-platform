package com.health.mapper;

import com.health.model.Models.Article;
import com.health.model.KnowledgeOptions.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface KnowledgeMapper {
  List<Article> list(@Param("query") String query, @Param("all") boolean all,
      @Param("userId") Long userId, @Param("category") Category category,
      @Param("sort") Sort sort, @Param("favoritesOnly") boolean favoritesOnly);
  Article published(@Param("id") Long id, @Param("userId") Long userId);
  void favorite(@Param("userId") Long userId, @Param("id") Long id);
  void unfavorite(@Param("userId") Long userId, @Param("id") Long id);

  Article get(Long id);

  void insert(Article article);

  int update(Article article);
}
