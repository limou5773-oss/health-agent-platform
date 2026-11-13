USE health_agent;

CREATE TABLE system_settings (
 id INT PRIMARY KEY CHECK(id=1), checkin_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 plan_due_enabled BOOLEAN NOT NULL DEFAULT TRUE, report_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 data_change_enabled BOOLEAN NOT NULL DEFAULT TRUE, import_failed_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 weekly_enabled BOOLEAN NOT NULL DEFAULT TRUE, monthly_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 import_analysis_enabled BOOLEAN NOT NULL DEFAULT TRUE, model_name VARCHAR(200) NOT NULL DEFAULT '',
 model_timeout_seconds INT NOT NULL DEFAULT 90 CHECK(model_timeout_seconds BETWEEN 5 AND 90)
);
INSERT INTO system_settings(id) VALUES(1);

ALTER TABLE agent_trigger_settings ADD COLUMN monthly_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agent_trigger_runs MODIFY COLUMN kind ENUM('WEEKLY_REVIEW','SLEEP_IMPORT','MONTHLY_REVIEW') NOT NULL;
