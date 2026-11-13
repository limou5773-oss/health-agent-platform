USE health_agent;

ALTER TABLE notifications MODIFY COLUMN kind
 ENUM('CHECKIN','PLAN_DUE','REPORT','DATA_CHANGE','IMPORT_FAILED','AUTO_TASK') NOT NULL;

CREATE TABLE agent_trigger_settings (
 user_id BIGINT PRIMARY KEY, weekly_enabled BOOLEAN NOT NULL, import_enabled BOOLEAN NOT NULL,
 import_days INT NOT NULL, source_name VARCHAR(100), notify_in_app BOOLEAN NOT NULL,
 CHECK(import_days BETWEEN 1 AND 31)
);
CREATE TABLE agent_trigger_runs (
 user_id BIGINT NOT NULL, kind ENUM('WEEKLY_REVIEW','SLEEP_IMPORT') NOT NULL,
 event_key VARCHAR(36) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
 source_name VARCHAR(100),
 task_id CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(user_id,kind,event_key),
 UNIQUE KEY uk_agent_trigger_period(user_id,kind,start_date,end_date),
 KEY ix_agent_trigger_runs_user(user_id,created_at)
);
