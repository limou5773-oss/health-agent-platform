-- Apply once to an existing schema after 007. MySQL 8.0.17+ is required.
USE health_agent;

CREATE TABLE plan_status_changes (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, plan_id BIGINT NOT NULL,
 previous_status ENUM('ACTIVE','PAUSED','ENDED') NOT NULL,
 status ENUM('ACTIVE','PAUSED','ENDED') NOT NULL,
 changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_plan_status_change_time(plan_id,changed_at),
 CHECK(previous_status <> status)
);
