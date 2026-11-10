package com.health.service;

import com.health.api.NutritionRequests;
import com.health.api.Requests.SleepQuery;
import com.health.model.NutritionModels.*;
import com.health.model.Models.Page;
import java.time.LocalDate;
import java.util.List;

public interface NutritionService {
  Page<MealRecord> meals(Long uid, SleepQuery query);
  MealRecord createMeal(Long uid, NutritionRequests.Meal request);
  MealRecord updateMeal(Long uid, Long id, NutritionRequests.Meal request);
  void deleteMeal(Long uid, Long id);
  List<String> mealSources(Long uid);
  MealStatistics mealStatistics(Long uid, LocalDate start, LocalDate end, String source);
  Page<WaterRecord> water(Long uid, SleepQuery query);
  WaterRecord createWater(Long uid, NutritionRequests.Water request);
  WaterRecord updateWater(Long uid, Long id, NutritionRequests.Water request);
  void deleteWater(Long uid, Long id);
  List<String> waterSources(Long uid);
  WaterStatistics waterStatistics(Long uid, LocalDate start, LocalDate end, String source);
}
