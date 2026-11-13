package com.health.mapper;

import com.health.model.ImportAdminModels.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ImportAdminMapper {
  List<Batch> batches(@Param("kind") Kind kind, @Param("issuesOnly") boolean issuesOnly,
      @Param("offset") long offset, @Param("size") int size);
  long count(@Param("kind") Kind kind, @Param("issuesOnly") boolean issuesOnly);
  Batch batch(@Param("kind") Kind kind, @Param("id") Long id);
  List<Issue> issues(@Param("kind") Kind kind, @Param("id") Long id, @Param("confirmed") boolean confirmed);
  void snapshotIssues(@Param("kind") Kind kind, @Param("id") Long id);
  String instructions(Kind kind);
  void saveInstructions(@Param("kind") Kind kind, @Param("instructions") String instructions);
  void rejected(@Param("kind") Kind kind, @Param("userId") Long userId, @Param("code") String code);
}
