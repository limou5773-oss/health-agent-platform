package com.health.model;

public record SystemSettings(boolean checkinEnabled, boolean planDueEnabled, boolean reportEnabled,
    boolean dataChangeEnabled, boolean importFailedEnabled, boolean weeklyEnabled, boolean monthlyEnabled,
    boolean importAnalysisEnabled, String modelName, int modelTimeoutSeconds) {}
