package com.health.mapper;

import com.health.model.Models.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AccountMapper {
  User byUsername(String username);

  User byId(Long id);
  Long lockUser(Long id);

  void insert(User user);

  void insertProfile(Long id);

  Profile profile(Long id);

  void updateProfile(Profile profile);

  void nickname(@Param("id") Long id, @Param("nickname") String nickname);

  void password(@Param("id") Long id, @Param("hash") String hash);

  List<User> users();

  List<User> lockAdminAccounts(@Param("actorId") Long actorId, @Param("targetId") Long targetId);
  void status(@Param("id") Long id, @Param("enabled") boolean enabled);
  void role(@Param("id") Long id, @Param("role") UserRole role);
  void insertAccountChange(@Param("actorId") Long actorId, @Param("targetId") Long targetId,
      @Param("field") String field, @Param("previousValue") String previousValue, @Param("newValue") String newValue);
  List<AccountChange> accountChanges();
}
