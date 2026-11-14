USE health_agent;

ALTER TABLE reports
  ADD COLUMN source_change ENUM('CORRECTED','DELETED','CHECKIN_REVOKED','DIARY_CHANGED') AFTER source_deleted;
