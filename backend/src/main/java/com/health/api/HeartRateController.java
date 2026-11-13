package com.health.api;

import com.health.model.HeartRateModels.*;
import com.health.model.Models.Page;
import com.health.security.Identity;
import com.health.service.HeartRateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/heart-rate")
public class HeartRateController {
  private final HeartRateService service;

  public HeartRateController(HeartRateService service) { this.service = service; }

  @GetMapping("/records")
  public Page<Sample> records(HttpServletRequest r, @Valid @ModelAttribute Requests.StatisticsQuery q) {
    return service.records(Identity.user(r).id, q);
  }

  @GetMapping("/statistics")
  public Statistics statistics(HttpServletRequest r, @Valid @ModelAttribute Requests.StatisticsQuery q) {
    return service.statistics(Identity.user(r).id, q);
  }

  @GetMapping("/sources")
  public List<String> sources(HttpServletRequest r) {
    return service.sources(Identity.user(r).id);
  }

  @PostMapping("/records")
  @ResponseStatus(HttpStatus.CREATED)
  public Sample create(HttpServletRequest r, @Valid @RequestBody Measurement q) {
    return service.save(Identity.user(r).id, null, q);
  }

  @PutMapping("/records/{id}")
  public Sample update(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Measurement q) {
    return service.save(Identity.user(r).id, id, q);
  }

  @DeleteMapping("/records/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest r, @PathVariable Long id) {
    service.delete(Identity.user(r).id, id);
  }

  public record Measurement(@NotNull LocalDateTime measuredAt,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 3) BigDecimal bpm,
      @NotBlank @Size(max = 100) String sourceName, @NotNull @Size(max = 1000) String note) {
    public Measurement {
      if (measuredAt != null) measuredAt = measuredAt.truncatedTo(ChronoUnit.SECONDS);
    }
  }
}
