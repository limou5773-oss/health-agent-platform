package com.health;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.health.mapper")
public class HealthApplication {
  public static void main(String[] args) {
    SpringApplication.run(HealthApplication.class, args);
  }
}
