package com.health.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

public final class Requests {
  private Requests() {}

  public record Register(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
      @NotNull String password,
      @NotBlank @Size(max = 40) String nickname) {
    @AssertTrue(message = "密码须为8至72个UTF-8字节")
    @JsonIgnore
    public boolean isPasswordValid() {
      return password == null || passwordBytes(password);
    }
  }

  public record Login(
      @NotBlank @Size(max = 32) String username, @NotBlank @Size(max = 72) String password) {
    @AssertTrue(message = "密码须为8至72个UTF-8字节")
    @JsonIgnore
    public boolean isPasswordValid() {
      return password == null || passwordBytes(password);
    }
  }

  public record Password(
      @NotBlank @Size(max = 72) String oldPassword, @NotNull String newPassword) {
    @AssertTrue(message = "原密码及新密码须为8至72个UTF-8字节")
    @JsonIgnore
    public boolean isPasswordValid() {
      return (oldPassword == null || passwordBytes(oldPassword))
          && (newPassword == null || passwordBytes(newPassword));
    }
  }

  public static boolean passwordBytes(String s) {
    int length = s.getBytes(StandardCharsets.UTF_8).length;
    return length >= 8 && length <= 72;
  }

  public record Profile(
      @NotBlank @Size(max = 40) String nickname,
      @DecimalMin("30") @DecimalMax("260") BigDecimal heightCm,
      @DecimalMin("1") @DecimalMax("500") BigDecimal weightKg,
      @NotBlank @Size(max = 64) String timezone,
      @NotNull @Size(max = 1000) String goal,
      @NotNull @Size(max = 2000) String preferences,
      @NotNull Boolean aiConsent) {
    @AssertTrue(message = "无效IANA时区")
    @JsonIgnore
    public boolean isTimezoneValid() {
      return timezone == null || ZoneId.getAvailableZoneIds().contains(timezone);
    }
  }

  public record Sleep(
      @NotNull LocalDateTime startTime,
      @NotNull LocalDateTime endTime,
      @NotBlank @Size(max = 100) String sourceName,
      @NotNull @Size(max = 1000) String note) {
    @AssertTrue(message = "睡眠结束须晚于开始且至少1秒")
    @JsonIgnore
    public boolean isIntervalValid() {
      return startTime == null
          || endTime == null
          || ChronoUnit.SECONDS.between(startTime, endTime) > 0;
    }
  }

  public static class ImportOptions {
    @NotBlank @Pattern(regexp = "CSV|APPLE_HEALTH")
    public String format = "CSV";

    public String timezone;

    @NotBlank @Pattern(regexp = "MI_FITNESS|APPLE_HEALTH|OTHER")
    public String sourceApp = "OTHER";

    @NotBlank @Pattern(regexp = "XIAOMI|REDMI|APPLE|OTHER")
    public String deviceBrand = "OTHER";

    @NotBlank @Pattern(regexp = "BAND|WATCH|OTHER")
    public String deviceType = "OTHER";

    @NotNull @Size(max = 80)
    public String deviceModel = "";

    public String getFormat() { return format; }
    public void setFormat(String value) { format = value; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String value) { timezone = value; }
    public String getSourceApp() { return sourceApp; }
    public void setSourceApp(String value) { sourceApp = value; }
    public String getDeviceBrand() { return deviceBrand; }
    public void setDeviceBrand(String value) { deviceBrand = value; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String value) { deviceType = value; }
    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String value) { deviceModel = value; }
  }

  public static class SleepQuery {
    @Min(1)
    public int page = 1;

    @Min(1)
    @Max(100)
    public int pageSize = 20;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    public LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    public LocalDate endDate;

    @Size(max = 100)
    public String sourceName;

    public int getPage() {
      return page;
    }

    public void setPage(int v) {
      page = v;
    }

    public int getPageSize() {
      return pageSize;
    }

    public void setPageSize(int v) {
      pageSize = v;
    }

    public LocalDate getStartDate() {
      return startDate;
    }

    public void setStartDate(LocalDate v) {
      startDate = v;
    }

    public LocalDate getEndDate() {
      return endDate;
    }

    public void setEndDate(LocalDate v) {
      endDate = v;
    }

    public String getSourceName() {
      return sourceName;
    }

    public void setSourceName(String v) {
      sourceName = v;
    }

    @AssertTrue(message = "日期范围无效")
    @JsonIgnore
    public boolean isRangeValid() {
      return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
  }

  public static class StatisticsQuery extends SleepQuery {
    @Override
    @NotNull
    public LocalDate getStartDate() {
      return startDate;
    }

    @Override
    @NotNull
    public LocalDate getEndDate() {
      return endDate;
    }

    @Override
    @AssertTrue(message = "日期范围须为1至366天")
    @JsonIgnore
    public boolean isRangeValid() {
      return startDate == null || endDate == null || validRange(startDate, endDate, 366);
    }
  }

  public record TaskCreate(
      @NotBlank @Size(max = 2000) String goal,
      @NotNull @Pattern(regexp = "ANALYZE|PLAN|REPORT") String kind,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @Size(max = 100) String sourceName,
      LocalDate planStartDate,
      LocalDate planEndDate,
      boolean includeDiary) {
    @AssertTrue(message = "PLAN须提供1至31天计划周期")
    @JsonIgnore
    public boolean isPlanRangeValid() {
      return !"PLAN".equals(kind)
          || (planStartDate != null
              && planEndDate != null
              && validRange(planStartDate, planEndDate, 31));
    }

    @AssertTrue(message = "日期范围须为1至366天")
    @JsonIgnore
    public boolean isRangeValid() {
      return startDate == null || endDate == null || validRange(startDate, endDate, 366);
    }
  }

  public record TaskInput(
      @NotBlank @Size(max = 4000) String input, @Size(max = 100) String sourceName) {}

  public record Draft(
      @Min(1) int version,
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 4000) String reason,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @NotNull @Size(min = 1, max = 93) List<@NotNull @Valid DraftItem> items) {
    @AssertTrue(message = "草稿周期须为1至31天，项目日期须在周期内")
    @JsonIgnore
    public boolean isRangeValid() {
      if (startDate == null || endDate == null || items == null) return true;
      return validRange(startDate, endDate, 31)
          && items.stream()
              .allMatch(
                  i ->
                      i == null
                          || i.date() == null
                          || (!i.date().isBefore(startDate) && !i.date().isAfter(endDate)));
    }
  }

  public record DraftItem(
      @NotNull LocalDate date,
      @NotNull LocalTime time,
      @NotBlank @Size(max = 200) String title,
      @NotNull @Size(max = 2000) String description) {}

  public record Confirm(@Min(1) int version, @NotBlank @Size(max = 100) String operationId) {}

  public record PlanStatus(@NotNull @Pattern(regexp = "ACTIVE|PAUSED|ENDED") String status) {}

  public record Checkin(@NotNull LocalDate date) {}

  public record UserStatus(@NotNull Boolean enabled) {}

  public record Article(
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 100000) String content,
      @NotBlank @Size(max = 200) String sourceName,
      @Size(max = 1000) String sourceUrl,
      @NotNull Boolean published,
      com.health.model.KnowledgeOptions.Category category) {
    @AssertTrue(message = "来源链接须为http或https")
    @JsonIgnore
    public boolean isUrlValid() {
      return sourceUrl == null || sourceUrl.isBlank() || sourceUrl.matches("https?://[^\\s]+");
    }
  }

  public record Step(
      @NotBlank @Size(max = 100) String name,
      @NotNull @Pattern(regexp = "RUNNING|COMPLETED|FAILED") String status,
      @Size(max = 1000) String inputSummary,
      @Size(max = 1000) String outputSummary) {}

  private static boolean validRange(LocalDate start, LocalDate end, long max) {
    long n = ChronoUnit.DAYS.between(start, end) + 1;
    return n >= 1 && n <= max;
  }
}
