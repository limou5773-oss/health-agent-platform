-- Run explicitly on an existing health_agent database after migrations 001-003.
-- Business IDs are logical associations; no database foreign keys or cascading actions.
USE health_agent;

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
