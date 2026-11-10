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
CREATE TABLE profiles (
 user_id BIGINT PRIMARY KEY, height_cm DECIMAL(5,2), weight_kg DECIMAL(6,2),
 timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai', goal VARCHAR(1000) NOT NULL DEFAULT '',
 preferences VARCHAR(2000) NOT NULL DEFAULT '', ai_consent BOOLEAN NOT NULL DEFAULT FALSE,
 CHECK(height_cm IS NULL OR height_cm BETWEEN 30 AND 260),
 CHECK(weight_kg IS NULL OR weight_kg BETWEEN 1 AND 500)
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
CREATE TABLE agent_tasks (
 id CHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL,
 kind ENUM('ANALYZE','PLAN','REPORT') NOT NULL, goal VARCHAR(2000) NOT NULL,
 status ENUM('QUEUED','RUNNING','WAITING_INPUT','WAITING_CONFIRMATION','COMPLETED','FAILED','CANCELLED') NOT NULL,
 start_date DATE NOT NULL, end_date DATE NOT NULL, plan_start_date DATE, plan_end_date DATE, source_name VARCHAR(100),
 answer MEDIUMTEXT, question TEXT, error VARCHAR(1000), retryable BOOLEAN NOT NULL DEFAULT FALSE,
 statistics_json JSON, draft_json JSON, task_token VARCHAR(100) NOT NULL, pending_input VARCHAR(4000),
 plan_id BIGINT, report_id BIGINT, confirmed_version INT, operation_id VARCHAR(100),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_task_user(user_id,created_at), CHECK(end_date >= start_date)
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
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_plan_user(user_id),
 CHECK(end_date >= start_date)
);
CREATE TABLE plan_items (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, plan_id BIGINT NOT NULL, date DATE NOT NULL,
 time TIME NOT NULL, title VARCHAR(200) NOT NULL, description VARCHAR(2000) NOT NULL,
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
 content MEDIUMTEXT, statistics_json JSON, references_json JSON, source_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_report_user(user_id)
);
CREATE TABLE knowledge_articles (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(200) NOT NULL, content MEDIUMTEXT NOT NULL,
 source_name VARCHAR(200) NOT NULL, source_url VARCHAR(1000), published BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
INSERT INTO knowledge_articles(title,content,source_name,source_url,published) VALUES
('平台睡眠统计口径','睡眠日期按醒来日期归属。同一来源同日的重叠时段按区间并集计算；多来源冲突须先选择来源。缺失日期不按零小时处理，覆盖率为有效日期占查询日期的比例。本条为平台计算规则，不是医学诊断或医学建议。','平台规则',NULL,TRUE);
