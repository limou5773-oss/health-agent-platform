package com.health.mapper;

import com.health.api.Requests.SleepQuery;
import com.health.model.NutritionModels.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NutritionMapper {
  Long lockUser(Long userId);
  List<MealRecord> meals(@Param("userId") Long uid, @Param("q") SleepQuery q, @Param("offset") long offset);
  long mealCount(@Param("userId") Long uid, @Param("q") SleepQuery q);
  MealRecord meal(@Param("userId") Long uid, @Param("id") Long id);
  List<MealRecord> mealRange(@Param("userId") Long uid, @Param("start") LocalDate start,
      @Param("end") LocalDate end, @Param("source") String source);
  void insertMeal(MealRecord record);
  void updateMeal(MealRecord record);
  void deleteMeal(@Param("userId") Long uid, @Param("id") Long id);
  List<String> mealSources(Long userId);
  List<WaterRecord> water(@Param("userId") Long uid, @Param("q") SleepQuery q, @Param("offset") long offset);
  long waterCount(@Param("userId") Long uid, @Param("q") SleepQuery q);
  WaterRecord waterRecord(@Param("userId") Long uid, @Param("id") Long id);
  List<WaterRecord> waterRange(@Param("userId") Long uid, @Param("start") LocalDate start,
      @Param("end") LocalDate end, @Param("source") String source);
  void insertWater(WaterRecord record);
  void updateWater(WaterRecord record);
  void deleteWater(@Param("userId") Long uid, @Param("id") Long id);
  List<String> waterSources(Long userId);
}
