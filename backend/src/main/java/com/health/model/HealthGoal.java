package com.health.model;

import com.health.api.HealthGoalRequests.Direction;
import com.health.model.BaselineModels.Metric;
import java.math.BigDecimal;
import java.time.LocalDate;

public class HealthGoal {
  public Long id;
  public String title;
  public LocalDate startDate;
  public LocalDate endDate;
  public Metric metric;
  public Direction direction;
  public BigDecimal targetValue;
  public String preferences;
}
