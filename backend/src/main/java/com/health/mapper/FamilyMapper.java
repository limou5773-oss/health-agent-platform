package com.health.mapper;

import com.health.model.FamilyModels.Grant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FamilyMapper {
  List<Grant> list(@Param("userId") Long userId);
  Grant readable(@Param("userId") Long userId, @Param("id") Long id);
  void invite(@Param("grant") Grant grant, @Param("days") int days);
  void removePair(@Param("ownerId") Long ownerId, @Param("memberId") Long memberId);
  int accept(@Param("userId") Long userId, @Param("id") Long id);
  int revoke(@Param("userId") Long userId, @Param("id") Long id);
}
