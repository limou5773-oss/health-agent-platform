USE health_agent;

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
 kind ENUM('CHECKIN','PLAN_DUE','REPORT','DATA_CHANGE','IMPORT_FAILED') NOT NULL,
 event_key VARCHAR(100) NOT NULL, title VARCHAR(100) NOT NULL, body VARCHAR(500) NOT NULL,
 target VARCHAR(200) NOT NULL, data_cutoff DATETIME(6),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), read_at DATETIME(6),
 UNIQUE KEY uk_notification_event(user_id,kind,event_key),
 KEY ix_notification_user(user_id,id),
 KEY ix_notification_unread(user_id,read_at,id)
);
