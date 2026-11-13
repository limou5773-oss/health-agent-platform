package com.health.model;

public final class KnowledgeOptions {
  private KnowledgeOptions() {}

  public enum Category { SLEEP, EXERCISE, NUTRITION, WELLBEING, GENERAL }
  public enum Sort { RELEVANCE, RECENT }
}
