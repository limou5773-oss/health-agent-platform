-- Run explicitly against your MySQL 8 server. The application never runs this file automatically.
-- Relationships use business IDs and application transactions, without database foreign keys.
CREATE DATABASE IF NOT EXISTS health_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE health_agent;

CREATE TABLE users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(32) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, nickname VARCHAR(40) NOT NULL,
 role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER', enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE family_grants (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 metrics_json JSON NOT NULL, report_ids_json JSON NOT NULL, accepted BOOLEAN NOT NULL DEFAULT FALSE,
 expires_at DATETIME NOT NULL, UNIQUE KEY uk_family_pair(owner_id,member_id),
 KEY ix_family_member(member_id,id)
);
CREATE TABLE profiles (
 user_id BIGINT PRIMARY KEY, height_cm DECIMAL(5,2), weight_kg DECIMAL(6,2),
 timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai', goal VARCHAR(1000) NOT NULL DEFAULT '',
 preferences VARCHAR(2000) NOT NULL DEFAULT '', ai_consent BOOLEAN NOT NULL DEFAULT FALSE,
 ai_sleep BOOLEAN NOT NULL DEFAULT TRUE, ai_activity BOOLEAN NOT NULL DEFAULT FALSE,
 ai_weight BOOLEAN NOT NULL DEFAULT FALSE, ai_heart_rate BOOLEAN NOT NULL DEFAULT FALSE,
 ai_diary BOOLEAN NOT NULL DEFAULT TRUE, ai_consent_version BIGINT NOT NULL DEFAULT 0,
 CHECK(height_cm IS NULL OR height_cm BETWEEN 30 AND 260),
 CHECK(weight_kg IS NULL OR weight_kg BETWEEN 1 AND 500)
);
CREATE TABLE notification_settings (
 user_id BIGINT PRIMARY KEY,
 checkin_enabled BOOLEAN NOT NULL,
 checkin_frequency ENUM('PER_TASK','DAILY') NOT NULL,
 daily_time TIME NOT NULL,
 plan_due_enabled BOOLEAN NOT NULL,
 report_enabled BOOLEAN NOT NULL,
 data_change_enabled BOOLEAN NOT NULL,
 import_failed_enabled BOOLEAN NOT NULL
);
CREATE TABLE notifications (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 kind ENUM('CHECKIN','PLAN_DUE','REPORT','DATA_CHANGE','IMPORT_FAILED','AUTO_TASK') NOT NULL,
 event_key VARCHAR(100) NOT NULL, title VARCHAR(100) NOT NULL, body VARCHAR(500) NOT NULL,
 target VARCHAR(200) NOT NULL, data_cutoff DATETIME(6),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), read_at DATETIME(6),
 UNIQUE KEY uk_notification_event(user_id,kind,event_key),
 KEY ix_notification_user(user_id,id),
 KEY ix_notification_unread(user_id,read_at,id)
);
CREATE TABLE system_settings (
 id INT PRIMARY KEY CHECK(id=1), checkin_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 plan_due_enabled BOOLEAN NOT NULL DEFAULT TRUE, report_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 data_change_enabled BOOLEAN NOT NULL DEFAULT TRUE, import_failed_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 weekly_enabled BOOLEAN NOT NULL DEFAULT TRUE, monthly_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 import_analysis_enabled BOOLEAN NOT NULL DEFAULT TRUE, model_name VARCHAR(200) NOT NULL DEFAULT '',
 model_timeout_seconds INT NOT NULL DEFAULT 90 CHECK(model_timeout_seconds BETWEEN 5 AND 90)
);
INSERT INTO system_settings(id) VALUES(1);
CREATE TABLE agent_trigger_settings (
 user_id BIGINT PRIMARY KEY, weekly_enabled BOOLEAN NOT NULL, import_enabled BOOLEAN NOT NULL,
 monthly_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 import_days INT NOT NULL, source_name VARCHAR(100), notify_in_app BOOLEAN NOT NULL,
 CHECK(import_days BETWEEN 1 AND 31)
);
CREATE TABLE agent_trigger_runs (
 user_id BIGINT NOT NULL, kind ENUM('WEEKLY_REVIEW','SLEEP_IMPORT','MONTHLY_REVIEW') NOT NULL,
 event_key VARCHAR(36) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
 source_name VARCHAR(100),
 task_id CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(user_id,kind,event_key),
 UNIQUE KEY uk_agent_trigger_period(user_id,kind,start_date,end_date),
 KEY ix_agent_trigger_runs_user(user_id,created_at)
);
CREATE TABLE user_preferences (
 user_id BIGINT NOT NULL,
 kind ENUM('GOAL','EXERCISE','ROUTINE','REMINDER') NOT NULL,
 content VARCHAR(500) NOT NULL,
 PRIMARY KEY(user_id,kind)
);
CREATE TABLE sleep_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL, sleep_date DATE NOT NULL,
 duration_minutes DECIMAL(12,4) NOT NULL, source_name VARCHAR(100) NOT NULL, note VARCHAR(1000) NOT NULL DEFAULT '',
 source_app VARCHAR(20) NOT NULL DEFAULT 'OTHER', device_brand VARCHAR(20) NOT NULL DEFAULT 'OTHER',
 device_type VARCHAR(20) NOT NULL DEFAULT 'OTHER', device_model VARCHAR(80) NOT NULL DEFAULT '',
 corrected BOOLEAN NOT NULL DEFAULT FALSE, dedup_key CHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_sleep_dedup(user_id,dedup_key),
 KEY ix_sleep_user_date(user_id,sleep_date), CHECK(end_time > start_time), CHECK(duration_minutes > 0)
);
CREATE TABLE import_batches (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 status ENUM('PREVIEW','CONFIRMED') NOT NULL DEFAULT 'PREVIEW', import_format VARCHAR(20) NOT NULL DEFAULT 'CSV', total_rows INT NOT NULL,
 source_app VARCHAR(20) NOT NULL DEFAULT 'OTHER', device_brand VARCHAR(20) NOT NULL DEFAULT 'OTHER',
 device_type VARCHAR(20) NOT NULL DEFAULT 'OTHER', device_model VARCHAR(80) NOT NULL DEFAULT '',
 valid_rows INT NOT NULL, invalid_rows INT NOT NULL, duplicate_rows INT NOT NULL,
 skipped_rows INT NOT NULL DEFAULT 0, unsupported_rows INT NOT NULL DEFAULT 0,
 inserted_rows INT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_import_user(user_id,created_at)
);
CREATE TABLE import_rows (
 batch_id BIGINT NOT NULL, row_number INT NOT NULL, start_time MEDIUMTEXT, end_time MEDIUMTEXT, sleep_date DATE,
 source_name MEDIUMTEXT, note MEDIUMTEXT, valid BOOLEAN NOT NULL, error VARCHAR(500), dedup_key CHAR(64),
 PRIMARY KEY(batch_id,row_number)
);
CREATE TABLE import_template_notes (
 kind ENUM('SLEEP','STEPS','WEIGHT','HEART_RATE') NOT NULL PRIMARY KEY, instructions VARCHAR(1000) NOT NULL
);
CREATE TABLE import_issue_counts (
 kind ENUM('SLEEP','STEPS','WEIGHT','HEART_RATE') NOT NULL, batch_id BIGINT NOT NULL,
 reason VARCHAR(40) NOT NULL, row_count INT NOT NULL,
 PRIMARY KEY(kind,batch_id,reason)
);
CREATE TABLE rejected_imports (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, kind ENUM('SLEEP','STEPS','WEIGHT','HEART_RATE') NOT NULL,
 user_id BIGINT NOT NULL, error_code VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_rejected_import_kind(kind,id)
);
CREATE TABLE plan_templates (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, version INT NOT NULL,
 category ENUM('SLEEP','EXERCISE','WATER') NOT NULL, title VARCHAR(200) NOT NULL,
 instructions VARCHAR(4000) NOT NULL, days INT NOT NULL, enabled BOOLEAN NOT NULL,
 items_json JSON NOT NULL, CHECK(days BETWEEN 1 AND 31)
);
CREATE TABLE agent_tasks (
 id CHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL,
 kind ENUM('ANALYZE','PLAN','REPORT','COMBINED') NOT NULL, goal VARCHAR(2000) NOT NULL,
 ai_consent_version BIGINT NOT NULL DEFAULT 0,
 parent_task_id CHAR(36),
 status ENUM('QUEUED','RUNNING','WAITING_INPUT','WAITING_CONFIRMATION','COMPLETED','FAILED','CANCELLED') NOT NULL,
 start_date DATE NOT NULL, end_date DATE NOT NULL, plan_start_date DATE, plan_end_date DATE, source_name VARCHAR(100),
 include_diary BOOLEAN NOT NULL DEFAULT FALSE,
 answer MEDIUMTEXT, question TEXT, error VARCHAR(1000), retryable BOOLEAN NOT NULL DEFAULT FALSE,
 statistics_json JSON, draft_json JSON, task_token VARCHAR(100) NOT NULL, pending_input VARCHAR(4000),
 plan_id BIGINT, report_id BIGINT, confirmed_version INT, operation_id VARCHAR(100),
 template_id BIGINT, template_version INT,
 duration_ms BIGINT,
 confirmed_at DATETIME(6), feedback ENUM('HELPFUL','NOT_HELPFUL'), feedback_at DATETIME(6),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_task_user(user_id,created_at), KEY ix_agent_task_parent(user_id,parent_task_id),
 CHECK(end_date >= start_date)
);
CREATE TABLE agent_steps (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id CHAR(36) NOT NULL,
 name VARCHAR(100) NOT NULL, status ENUM('RUNNING','COMPLETED','FAILED') NOT NULL,
 input_summary VARCHAR(1000), output_summary VARCHAR(1000),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_step_task(task_id)
);
CREATE TABLE plans (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, task_id CHAR(36) UNIQUE,
 title VARCHAR(200) NOT NULL, reason TEXT NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
 status ENUM('ACTIVE','PAUSED','ENDED') NOT NULL DEFAULT 'ACTIVE',
 version INT NOT NULL DEFAULT 1,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_plan_user(user_id),
 CHECK(end_date >= start_date)
);
CREATE TABLE plan_revisions (
 plan_id BIGINT NOT NULL, version INT NOT NULL,
 status ENUM('DRAFT','CONFIRMED','DISCARDED') NOT NULL,
 note VARCHAR(1000) NOT NULL, content_json JSON,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(plan_id,version)
);
CREATE TABLE plan_status_changes (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, plan_id BIGINT NOT NULL,
 previous_status ENUM('ACTIVE','PAUSED','ENDED') NOT NULL,
 status ENUM('ACTIVE','PAUSED','ENDED') NOT NULL,
 changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_plan_status_change_time(plan_id,changed_at),
 CHECK(previous_status <> status)
);
CREATE TABLE plan_items (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, plan_id BIGINT NOT NULL, date DATE NOT NULL,
 time TIME NOT NULL, title VARCHAR(200) NOT NULL, description VARCHAR(2000) NOT NULL,
 reminder_time TIME, retired BOOLEAN NOT NULL DEFAULT FALSE,
 missed_reason VARCHAR(500) NOT NULL DEFAULT '',
 KEY ix_item_plan(plan_id)
);
CREATE TABLE checkins (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, item_id BIGINT NOT NULL, date DATE NOT NULL,
 checked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_checkin_item_date(item_id,date)
);
CREATE TABLE reports (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, task_id CHAR(36) UNIQUE,
 title VARCHAR(200) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL, source_name VARCHAR(100),
 include_diary BOOLEAN NOT NULL DEFAULT FALSE,
 content MEDIUMTEXT, statistics_json JSON, references_json JSON, source_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 report_type ENUM('SLEEP','HEALTH_WEEK','HEALTH_MONTH','HEALTH_FOCUSED','HEALTH_COMPARISON') NOT NULL DEFAULT 'SLEEP',
 report_version INT NOT NULL DEFAULT 1,
 metric VARCHAR(16), comparison_start_date DATE, comparison_end_date DATE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_report_user(user_id),
 KEY ix_report_period(user_id,report_type,start_date,end_date,report_version)
);
CREATE TABLE knowledge_articles (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(200) NOT NULL, content MEDIUMTEXT NOT NULL,
 source_name VARCHAR(200) NOT NULL, source_url VARCHAR(1000), published BOOLEAN NOT NULL DEFAULT FALSE,
 category ENUM('SLEEP','EXERCISE','NUTRITION','WELLBEING','GENERAL') NOT NULL DEFAULT 'GENERAL',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
INSERT INTO knowledge_articles(title,content,source_name,source_url,published,category) VALUES
('平台睡眠统计口径','睡眠日期按醒来日期归属。同一来源同日的重叠时段按区间并集计算；多来源冲突须先选择来源。缺失日期不按零小时处理，覆盖率为有效日期占查询日期的比例。本条为平台计算规则，不是医学诊断或医学建议。','平台规则',NULL,TRUE,'SLEEP');

CREATE TABLE knowledge_favorites (
 user_id BIGINT NOT NULL, article_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,article_id)
);

-- Case/accent-sensitive identities keep SQL uniqueness consistent with Java source/type comparisons.
CREATE TABLE step_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL, activity_date DATE NOT NULL,
 steps INT NOT NULL, mode ENUM('CUMULATIVE','DELTA') NOT NULL,
 source_name VARCHAR(100) COLLATE utf8mb4_0900_as_cs NOT NULL, note VARCHAR(1000) NOT NULL DEFAULT '',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_step_identity(user_id,source_name,start_time,end_time),
 KEY ix_step_user_date(user_id,activity_date),
 CHECK(steps >= 0), CHECK(end_time > start_time),
 CHECK(activity_date = DATE(start_time)),
 CHECK(end_time <= DATE_ADD(activity_date, INTERVAL 1 DAY)),
 CHECK(mode != 'CUMULATIVE' OR TIME(start_time) = '00:00:00')
);
CREATE TABLE activity_goals (
 user_id BIGINT PRIMARY KEY, daily_steps INT NOT NULL,
 CHECK(daily_steps > 0)
);
CREATE TABLE step_import_batches (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 status ENUM('PREVIEW','CONFIRMED') NOT NULL DEFAULT 'PREVIEW',
 total_rows INT NOT NULL, valid_rows INT NOT NULL, invalid_rows INT NOT NULL,
 duplicate_rows INT NOT NULL, inserted_rows INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_step_import_user(user_id,created_at)
);
CREATE TABLE step_import_rows (
 batch_id BIGINT NOT NULL, row_number INT NOT NULL,
 start_time MEDIUMTEXT, end_time MEDIUMTEXT, steps MEDIUMTEXT, mode MEDIUMTEXT,
 source_name MEDIUMTEXT, note MEDIUMTEXT, valid BOOLEAN NOT NULL, error VARCHAR(500),
 PRIMARY KEY(batch_id,row_number)
);
CREATE TABLE workout_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 exercise_type VARCHAR(50) COLLATE utf8mb4_0900_as_cs NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL, activity_date DATE NOT NULL,
 duration_minutes DECIMAL(16,4) NOT NULL,
 source_name VARCHAR(100) COLLATE utf8mb4_0900_as_cs NOT NULL, note VARCHAR(1000) NOT NULL DEFAULT '',
 plan_item_id BIGINT, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_workout_identity(user_id,start_time,end_time,exercise_type,source_name),
 KEY ix_workout_user_date(user_id,activity_date), KEY ix_workout_item(plan_item_id),
 CHECK(end_time > start_time), CHECK(duration_minutes > 0),
 CHECK(activity_date = DATE(start_time))
);

-- Binary NO PAD identities match Java's exact food/source comparisons.
CREATE TABLE meal_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 consumed_at DATETIME NOT NULL, meal_type ENUM('BREAKFAST','LUNCH','DINNER','SNACK') NOT NULL,
 food_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
 portion_amount DECIMAL(12,3), portion_unit ENUM('G','ML','SERVING') NOT NULL,
 calorie_mode ENUM('UNKNOWN','MANUAL','ESTIMATED') NOT NULL,
 calories_kcal DECIMAL(26,2), reference_amount DECIMAL(12,3), reference_calories_kcal DECIMAL(12,2),
 nutrition_source VARCHAR(500) NOT NULL,
 source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, note VARCHAR(1000) NOT NULL DEFAULT '',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_meal_identity(user_id,consumed_at,meal_type,food_name,source_name),
 KEY ix_meal_user_source_time(user_id,source_name,consumed_at),
 CHECK(portion_amount IS NULL OR portion_amount > 0),
 CHECK(reference_amount IS NULL OR reference_amount > 0),
 CHECK(calories_kcal IS NULL OR calories_kcal >= 0),
 CHECK(reference_calories_kcal IS NULL OR reference_calories_kcal >= 0),
 CHECK(
   (calorie_mode = 'UNKNOWN' AND calories_kcal IS NULL AND reference_amount IS NULL AND reference_calories_kcal IS NULL)
   OR (calorie_mode = 'MANUAL' AND calories_kcal IS NOT NULL AND calories_kcal <= 9999999999.99
       AND reference_amount IS NULL AND reference_calories_kcal IS NULL)
   OR (calorie_mode = 'ESTIMATED' AND calories_kcal IS NOT NULL AND portion_amount IS NOT NULL
       AND reference_amount IS NOT NULL AND reference_calories_kcal IS NOT NULL AND CHAR_LENGTH(TRIM(nutrition_source)) > 0)
 )
);
CREATE TABLE water_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 consumed_at DATETIME NOT NULL, amount_ml DECIMAL(11,2) NOT NULL,
 source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, note VARCHAR(1000) NOT NULL DEFAULT '',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_water_identity(user_id,consumed_at,amount_ml,source_name),
 KEY ix_water_user_source_time(user_id,source_name,consumed_at),
 CHECK(amount_ml > 0)
);

-- Binary NO PAD source identities match Java's exact comparisons.
CREATE TABLE heart_rate_samples (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, measured_at DATETIME NOT NULL,
 bpm DECIMAL(7,3) NOT NULL CHECK(bpm > 0),
 source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, note VARCHAR(1000) NOT NULL,
 corrected BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE KEY uk_heart_rate_sample(user_id,measured_at,source_name)
);
CREATE TABLE weight_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 measured_at DATETIME NOT NULL, weight_kg DECIMAL(7,3) NOT NULL,
 source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, note VARCHAR(1000) NOT NULL DEFAULT '',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_weight_identity(user_id,measured_at,source_name),
 KEY ix_weight_user_source_time(user_id,source_name,measured_at),
 CHECK(weight_kg > 0)
);
CREATE TABLE measurement_import_batches (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 metric ENUM('WEIGHT','HEART_RATE') NOT NULL, status ENUM('PREVIEW','CONFIRMED') NOT NULL,
 total_rows INT NOT NULL, duplicate_rows INT NOT NULL, inserted_rows INT NOT NULL DEFAULT 0,
 first_measurement DATETIME NOT NULL, last_measurement DATETIME NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, confirmed_at DATETIME NULL,
 KEY ix_measurement_import_history(user_id,metric,id)
);
CREATE TABLE measurement_import_rows (
 batch_id BIGINT NOT NULL, row_number INT NOT NULL, measured_at DATETIME NOT NULL,
 measurement_value DECIMAL(7,3) NOT NULL, source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
 note VARCHAR(1000) NOT NULL,
 PRIMARY KEY(batch_id,row_number)
);
CREATE TABLE weight_goals (
 user_id BIGINT PRIMARY KEY, start_date DATE NOT NULL, target_date DATE NOT NULL,
 target_weight_kg DECIMAL(7,3) NOT NULL, source_name VARCHAR(100) COLLATE utf8mb4_0900_bin,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(target_weight_kg > 0),
 CHECK(DATEDIFF(target_date,start_date) BETWEEN 0 AND 365)
);

-- Diary entries remain independent even when their recorded times are identical.
CREATE TABLE diary_entries (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 recorded_at DATETIME NOT NULL, mood VARCHAR(40) NOT NULL, stress_level INT,
 body_feeling VARCHAR(500) NOT NULL, events VARCHAR(1000) NOT NULL,
 tags VARCHAR(200) NOT NULL, note VARCHAR(2000) NOT NULL,
 source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
 agent_allowed BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_diary_user_time(user_id,recorded_at,id),
 KEY ix_diary_user_permission_time(user_id,agent_allowed,recorded_at,id),
 CHECK(stress_level IS NULL OR stress_level BETWEEN 0 AND 10)
);

CREATE TABLE checkup_reports (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 report_date DATE NOT NULL, title VARCHAR(120) NOT NULL, items_json JSON NOT NULL,
 KEY ix_checkup_user_date(user_id,report_date,id)
);
