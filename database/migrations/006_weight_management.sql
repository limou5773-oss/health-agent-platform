-- Apply once to an existing schema after 005. MySQL 8.0.17+ is required.
-- Source identities use binary NO PAD comparison, matching Java's exact strings.
USE health_agent;

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
CREATE TABLE weight_goals (
 user_id BIGINT PRIMARY KEY, start_date DATE NOT NULL, target_date DATE NOT NULL,
 target_weight_kg DECIMAL(7,3) NOT NULL, source_name VARCHAR(100) COLLATE utf8mb4_0900_bin,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(target_weight_kg > 0),
 CHECK(DATEDIFF(target_date,start_date) BETWEEN 0 AND 365)
);
