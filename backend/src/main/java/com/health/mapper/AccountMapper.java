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

  int status(@Param("id") Long id, @Param("enabled") boolean enabled);
}
