package com.health.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.Set;

public record PlanCoordinationRequest(
    @NotEmpty @Size(max = 20) Set<@NotNull @Positive Long> priorityPlanIds,
    @NotNull LocalTime availableFrom,
    @NotNull LocalTime availableUntil) {}
