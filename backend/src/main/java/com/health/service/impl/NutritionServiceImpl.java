package com.health.service.impl;

import com.health.api.*;
import com.health.mapper.NutritionMapper;
import com.health.model.NutritionModels.*;
import com.health.model.Models.Page;
import com.health.service.NutritionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionServiceImpl implements NutritionService {
  private final NutritionMapper mapper;

  public NutritionServiceImpl(NutritionMapper mapper) { this.mapper = mapper; }

  public Page<MealRecord> meals(Long uid, Requests.SleepQuery q) {
    return new Page<>(mapper.meals(uid, q, (q.page - 1L) * q.pageSize), mapper.mealCount(uid, q), q.page, q.pageSize);
  }

  @Transactional
  public MealRecord createMeal(Long uid, NutritionRequests.Meal request) {
    mapper.lockUser(uid);
    MealRecord record = mealRecord(uid, request);
    mapper.insertMeal(record);
    return mapper.meal(uid, record.id);
  }

  @Transactional
  public MealRecord updateMeal(Long uid, Long id, NutritionRequests.Meal request) {
    mapper.lockUser(uid);
    if (mapper.meal(uid, id) == null) throw ApiException.missing();
    MealRecord record = mealRecord(uid, request);
    record.id = id;
    mapper.updateMeal(record);
    return mapper.meal(uid, id);
  }

  @Transactional
  public void deleteMeal(Long uid, Long id) {
    mapper.lockUser(uid);
    if (mapper.meal(uid, id) == null) throw ApiException.missing();
    mapper.deleteMeal(uid, id);
  }

  public List<String> mealSources(Long uid) { return mapper.mealSources(uid); }

  private static MealRecord mealRecord(Long uid, NutritionRequests.Meal request) {
    MealRecord record = new MealRecord();
    record.userId = uid;
    record.consumedAt = request.consumedAt();
    record.mealType = request.mealType();
    record.foodName = request.foodName();
    record.portionAmount = request.portionAmount();
    record.portionUnit = request.portionUnit();
    record.calorieMode = request.calorieMode();
    record.caloriesKcal = "ESTIMATED".equals(request.calorieMode())
        ? request.portionAmount().multiply(request.referenceCaloriesKcal())
            .divide(request.referenceAmount(), 2, RoundingMode.HALF_UP)
        : request.caloriesKcal();
    record.referenceAmount = request.referenceAmount();
    record.referenceCaloriesKcal = request.referenceCaloriesKcal();
    record.nutritionSource = request.nutritionSource();
    record.sourceName = request.sourceName();
    record.note = request.note();
    return record;
  }

  public MealStatistics mealStatistics(Long uid, LocalDate start, LocalDate end, String source) {
    List<MealRecord> records = mapper.mealRange(uid, start, end, source);
    Map<LocalDate, List<MealRecord>> days = new HashMap<>();
    Map<MealEvent, String> eventSources = new HashMap<>();
    LocalDateTime cutoff = null;
    for (MealRecord record : records) {
      String previous = eventSources.putIfAbsent(
          new MealEvent(record.consumedAt, record.mealType, record.foodName), record.sourceName);
      if (previous != null && !previous.equals(record.sourceName))
        throw new ApiException(409, "SOURCE_SELECTION_REQUIRED", "同秒同食物和餐次存在不同来源，请先选择来源");
      days.computeIfAbsent(record.consumedAt.toLocalDate(), key -> new ArrayList<>()).add(record);
      if (cutoff == null || record.consumedAt.isAfter(cutoff)) cutoff = record.consumedAt;
    }

    List<MealDaily> daily = new ArrayList<>();
    int manualCount = 0, estimatedCount = 0, unknownCount = 0;
    BigDecimal manualTotal = null, estimatedTotal = null;
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      List<MealRecord> bucket = days.get(date);
      if (bucket == null) {
        daily.add(new MealDaily(date, 0, 0, 0, 0, 0, null, null, null));
        continue;
      }
      int manual = 0, estimated = 0, unknown = 0;
      BigDecimal manualKcal = null, estimatedKcal = null;
      for (MealRecord record : bucket) {
        switch (record.calorieMode) {
          case "MANUAL" -> {
            manual++;
            manualKcal = addKnown(manualKcal, record.caloriesKcal);
          }
          case "ESTIMATED" -> {
            estimated++;
            estimatedKcal = addKnown(estimatedKcal, record.caloriesKcal);
          }
          case "UNKNOWN" -> unknown++;
          default -> throw new IllegalStateException("Unsupported saved calorie mode: " + record.calorieMode);
        }
      }
      manualCount += manual;
      estimatedCount += estimated;
      unknownCount += unknown;
      manualTotal = addKnown(manualTotal, manualKcal);
      estimatedTotal = addKnown(estimatedTotal, estimatedKcal);
      daily.add(new MealDaily(date, bucket.size(), manual + estimated, unknown, manual, estimated,
          manualKcal, estimatedKcal, addKnown(manualKcal, estimatedKcal)));
    }
    long period = ChronoUnit.DAYS.between(start, end) + 1;
    return new MealStatistics(start, end, source, period, days.size(), coverage(days.size(), period),
        records.size(), manualCount + estimatedCount, unknownCount, manualCount, estimatedCount,
        manualTotal, estimatedTotal, addKnown(manualTotal, estimatedTotal), cutoff, daily);
  }

  public Page<WaterRecord> water(Long uid, Requests.SleepQuery q) {
    return new Page<>(mapper.water(uid, q, (q.page - 1L) * q.pageSize), mapper.waterCount(uid, q), q.page, q.pageSize);
  }

  @Transactional
  public WaterRecord createWater(Long uid, NutritionRequests.Water request) {
    mapper.lockUser(uid);
    WaterRecord record = waterRecord(uid, request);
    mapper.insertWater(record);
    return mapper.waterRecord(uid, record.id);
  }

  @Transactional
  public WaterRecord updateWater(Long uid, Long id, NutritionRequests.Water request) {
    mapper.lockUser(uid);
    if (mapper.waterRecord(uid, id) == null) throw ApiException.missing();
    WaterRecord record = waterRecord(uid, request);
    record.id = id;
    mapper.updateWater(record);
    return mapper.waterRecord(uid, id);
  }

  @Transactional
  public void deleteWater(Long uid, Long id) {
    mapper.lockUser(uid);
    if (mapper.waterRecord(uid, id) == null) throw ApiException.missing();
    mapper.deleteWater(uid, id);
  }

  public List<String> waterSources(Long uid) { return mapper.waterSources(uid); }

  private static WaterRecord waterRecord(Long uid, NutritionRequests.Water request) {
    WaterRecord record = new WaterRecord();
    record.userId = uid;
    record.consumedAt = request.consumedAt();
    record.amountMl = request.amountMl();
    record.sourceName = request.sourceName();
    record.note = request.note();
    return record;
  }

  public WaterStatistics waterStatistics(Long uid, LocalDate start, LocalDate end, String source) {
    List<WaterRecord> records = mapper.waterRange(uid, start, end, source);
    Map<LocalDate, List<WaterRecord>> days = new HashMap<>();
    Map<WaterEvent, String> eventSources = new HashMap<>();
    LocalDateTime cutoff = null;
    for (WaterRecord record : records) {
      String previous = eventSources.putIfAbsent(
          new WaterEvent(record.consumedAt, record.amountMl.stripTrailingZeros()), record.sourceName);
      if (previous != null && !previous.equals(record.sourceName))
        throw new ApiException(409, "SOURCE_SELECTION_REQUIRED", "同秒同水量存在不同来源，请先选择来源");
      days.computeIfAbsent(record.consumedAt.toLocalDate(), key -> new ArrayList<>()).add(record);
      if (cutoff == null || record.consumedAt.isAfter(cutoff)) cutoff = record.consumedAt;
    }

    List<WaterDaily> daily = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      List<WaterRecord> bucket = days.get(date);
      if (bucket == null) {
        daily.add(new WaterDaily(date, null, 0));
        continue;
      }
      BigDecimal amount = bucket.stream().map(record -> record.amountMl).reduce(BigDecimal.ZERO, BigDecimal::add);
      total = total.add(amount);
      daily.add(new WaterDaily(date, amount, bucket.size()));
    }
    long period = ChronoUnit.DAYS.between(start, end) + 1;
    BigDecimal average = days.isEmpty() ? null : total.divide(BigDecimal.valueOf(days.size()), 2, RoundingMode.HALF_UP);
    return new WaterStatistics(start, end, source, period, days.size(), coverage(days.size(), period),
        records.size(), total, average, cutoff, daily);
  }

  private record MealEvent(LocalDateTime consumedAt, String mealType, String foodName) {}
  private record WaterEvent(LocalDateTime consumedAt, BigDecimal amountMl) {}

  // Null means no known value; an explicitly recorded zero remains a known value.
  private static BigDecimal addKnown(BigDecimal left, BigDecimal right) {
    if (left == null) return right;
    return right == null ? left : left.add(right);
  }

  private static BigDecimal coverage(long valid, long period) {
    return BigDecimal.valueOf(valid).multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
  }
}
