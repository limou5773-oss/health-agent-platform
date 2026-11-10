package com.health.config;

import com.health.security.*;
import com.health.service.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthInterceptor auth;
  private final InternalInterceptor internal;

  public WebConfig(AuthInterceptor auth, InternalInterceptor internal) {
    this.auth = auth;
    this.internal = internal;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(auth)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/register", "/api/auth/login", "/api/health");
    registry.addInterceptor(internal).addPathPatterns("/internal/agent/tasks/**");
  }

  @Bean(destroyMethod = "shutdown")
  public static ExecutorService taskExecutor() {
    return Executors.newFixedThreadPool(2);
  }

  @Bean
  public ApplicationRunner startup(
      AccountService accounts,
      TaskService tasks,
      @Value("${app.admin-username}") String username,
      @Value("${app.admin-password}") String password) {
    return args -> {
      accounts.initializeAdmin(username, password);
      tasks.recoverInterrupted();
    };
  }
}
