package com.health.mapper;

import com.health.model.PreferenceModels.Kind;
import com.health.model.PreferenceModels.Preference;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PreferenceMapper {
  List<Preference> list(Long userId);

  void save(@Param("userId") Long userId, @Param("kind") Kind kind, @Param("content") String content);

  void delete(@Param("userId") Long userId, @Param("kind") Kind kind);
}
