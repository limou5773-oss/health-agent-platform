package com.health.api;

import com.health.model.Models.*;
import com.health.security.Identity;
import com.health.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdminController {
  private final AccountService accounts;
  private final KnowledgeService knowledge;
  private final TaskService tasks;

  public AdminController(AccountService accounts, KnowledgeService knowledge, TaskService tasks) {
    this.accounts = accounts;
    this.knowledge = knowledge;
    this.tasks = tasks;
  }

  @GetMapping("/knowledge")
  public List<Article> knowledge(@RequestParam(required = false) @Size(max = 200) String query) {
    return knowledge.list(query, false);
  }

  @GetMapping("/admin/users")
  public List<User> users() {
    return accounts.users();
  }

  @PatchMapping("/admin/users/{id}/status")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void status(
      HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Requests.UserStatus q) {
    accounts.status(Identity.user(r).id, id, q.enabled());
  }

  @GetMapping("/admin/tasks")
  public List<Map<String, Object>> tasks() {
    return tasks.admin();
  }

  @GetMapping("/admin/knowledge")
  public List<Article> all() {
    return knowledge.list(null, true);
  }

  @PostMapping("/admin/knowledge")
  @ResponseStatus(HttpStatus.CREATED)
  public Article create(@Valid @RequestBody Requests.Article q) {
    return knowledge.save(null, q);
  }

  @PutMapping("/admin/knowledge/{id}")
  public Article update(@PathVariable Long id, @Valid @RequestBody Requests.Article q) {
    return knowledge.save(id, q);
  }
}
