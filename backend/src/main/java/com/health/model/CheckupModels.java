package com.health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class CheckupModels {
  private CheckupModels() {}

  public record Item(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 80) String value,
      @NotNull @Size(max = 50) String unit, @NotNull @Size(max = 200) String referenceRange,
      @NotBlank @Size(max = 180) String location, @NotBlank @Size(max = 400) String excerpt) {}

  public record Draft(@NotNull LocalDate reportDate, @NotBlank @Size(max = 120) String title,
      @NotEmpty @Size(max = 50) List<@Valid @NotNull Item> items) {}

  public record Preview(LocalDate reportDate, List<Item> items) {}

  public static class Report {
    public Long id;
    @JsonIgnore public Long userId;
    public LocalDate reportDate;
    public String title;
    @JsonIgnore public String itemsJson;
    public List<Item> items;
  }
}
