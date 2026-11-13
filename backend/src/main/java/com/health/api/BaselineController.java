package com.health.api;

import com.health.model.BaselineModels.Comparison;
import com.health.security.Identity;
import com.health.service.BaselineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/baseline")
public class BaselineController {
  private final BaselineService service;

  public BaselineController(BaselineService service) {
    this.service = service;
  }

  @GetMapping
  public Comparison compare(HttpServletRequest request, @Valid @ModelAttribute BaselineRequests.Query query) {
    return service.compare(Identity.user(request).id, query);
  }
}
