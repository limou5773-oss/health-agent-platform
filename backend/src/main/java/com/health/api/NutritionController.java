package com.health.api;

import com.health.model.NutritionModels.*;
import com.health.model.Models.Page;
import com.health.security.Identity;
import com.health.service.NutritionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {
  private final NutritionService service;

  public NutritionController(NutritionService service) { this.service = service; }

  @GetMapping("/meals")
  public Page<MealRecord> meals(HttpServletRequest r, @Valid @ModelAttribute Requests.SleepQuery q) {
    return service.meals(Identity.user(r).id, q);
  }

  @PostMapping("/meals")
  @ResponseStatus(HttpStatus.CREATED)
  public MealRecord createMeal(HttpServletRequest r, @Valid @RequestBody NutritionRequests.Meal q) {
    return service.createMeal(Identity.user(r).id, q);
  }

  @PutMapping("/meals/{id}")
  public MealRecord updateMeal(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody NutritionRequests.Meal q) {
    return service.updateMeal(Identity.user(r).id, id, q);
  }

  @DeleteMapping("/meals/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMeal(HttpServletRequest r, @PathVariable Long id) { service.deleteMeal(Identity.user(r).id, id); }

  @GetMapping("/meals/sources")
  public List<String> mealSources(HttpServletRequest r) { return service.mealSources(Identity.user(r).id); }

  @GetMapping("/meals/statistics")
  public MealStatistics mealStatistics(HttpServletRequest r, @Valid @ModelAttribute Requests.StatisticsQuery q) {
    return service.mealStatistics(Identity.user(r).id, q.startDate, q.endDate, q.sourceName);
  }

  @GetMapping("/water")
  public Page<WaterRecord> water(HttpServletRequest r, @Valid @ModelAttribute Requests.SleepQuery q) {
    return service.water(Identity.user(r).id, q);
  }

  @PostMapping("/water")
  @ResponseStatus(HttpStatus.CREATED)
  public WaterRecord createWater(HttpServletRequest r, @Valid @RequestBody NutritionRequests.Water q) {
    return service.createWater(Identity.user(r).id, q);
  }

  @PutMapping("/water/{id}")
  public WaterRecord updateWater(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody NutritionRequests.Water q) {
    return service.updateWater(Identity.user(r).id, id, q);
  }

  @DeleteMapping("/water/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWater(HttpServletRequest r, @PathVariable Long id) { service.deleteWater(Identity.user(r).id, id); }

  @GetMapping("/water/sources")
  public List<String> waterSources(HttpServletRequest r) { return service.waterSources(Identity.user(r).id); }

  @GetMapping("/water/statistics")
  public WaterStatistics waterStatistics(HttpServletRequest r, @Valid @ModelAttribute Requests.StatisticsQuery q) {
    return service.waterStatistics(Identity.user(r).id, q.startDate, q.endDate, q.sourceName);
  }
}
