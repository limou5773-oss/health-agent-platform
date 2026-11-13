package com.health.api;

import com.health.model.PreferenceModels.Kind;
import com.health.model.PreferenceModels.Preference;
import com.health.security.Identity;
import com.health.service.PreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile/preferences")
public class PreferenceController {
  private final PreferenceService preferences;

  public PreferenceController(PreferenceService preferences) {
    this.preferences = preferences;
  }

  @GetMapping
  public List<Preference> list(HttpServletRequest request) {
    return preferences.list(Identity.user(request).id);
  }

  @PutMapping("/{kind}")
  public Preference save(HttpServletRequest request, @PathVariable Kind kind, @Valid @RequestBody Save body) {
    return preferences.save(Identity.user(request).id, kind, body.content());
  }

  @DeleteMapping("/{kind}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest request, @PathVariable Kind kind) {
    preferences.delete(Identity.user(request).id, kind);
  }

  public record Save(@NotBlank @Size(max = 500) String content) {}
}
