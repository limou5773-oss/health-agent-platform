package com.health.model;

public final class PreferenceModels {
  private PreferenceModels() {}

  public enum Kind {
    GOAL, EXERCISE, ROUTINE, REMINDER
  }

  public record Preference(Kind kind, String content) {}
}
