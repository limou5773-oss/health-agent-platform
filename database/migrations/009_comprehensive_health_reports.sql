USE health_agent;

ALTER TABLE reports
 ADD COLUMN report_type ENUM('SLEEP','HEALTH_WEEK','HEALTH_MONTH') NOT NULL DEFAULT 'SLEEP' AFTER source_deleted,
 ADD COLUMN report_version INT NOT NULL DEFAULT 1 AFTER report_type,
 ADD KEY ix_report_period(user_id,report_type,start_date,end_date,report_version);
