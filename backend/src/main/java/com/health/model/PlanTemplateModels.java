package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalTime;
import java.util.List;

public final class PlanTemplateModels {
  private PlanTemplateModels() {}

  public enum Category { SLEEP, EXERCISE, WATER }

  public record Item(int day, LocalTime time, String title, String description) {}

  public static class Template {
    public Long id;
    public int version;
    public Category category;
    public String title;
    public String instructions;
    public int days;
    public boolean enabled;
    @JsonIgnore public String itemsJson;
    public List<Item> items;
  }
}
