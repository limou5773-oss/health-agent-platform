USE health_agent;

CREATE TABLE checkup_reports (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 report_date DATE NOT NULL, title VARCHAR(120) NOT NULL, items_json JSON NOT NULL,
 KEY ix_checkup_user_date(user_id,report_date,id)
);
