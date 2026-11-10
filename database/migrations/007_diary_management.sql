-- Apply once to an existing schema after 006. MySQL 8.0.17+ is required.
-- Relationships use application transactions and business IDs, without foreign keys.
USE health_agent;

ALTER TABLE agent_tasks ADD COLUMN include_diary BOOLEAN NOT NULL DEFAULT FALSE AFTER source_name;
ALTER TABLE reports ADD COLUMN include_diary BOOLEAN NOT NULL DEFAULT FALSE AFTER source_name;

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
