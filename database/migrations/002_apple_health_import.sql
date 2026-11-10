-- Apply explicitly once to an existing health_agent database. Fresh installations use schema.sql.
USE health_agent;

ALTER TABLE sleep_records MODIFY COLUMN duration_minutes DECIMAL(12,4) NOT NULL;
ALTER TABLE import_batches
 ADD COLUMN import_format VARCHAR(20) NOT NULL DEFAULT 'CSV' AFTER status,
 ADD COLUMN skipped_rows INT NOT NULL DEFAULT 0 AFTER duplicate_rows,
 ADD COLUMN unsupported_rows INT NOT NULL DEFAULT 0 AFTER skipped_rows;
ALTER TABLE import_rows ADD COLUMN sleep_date DATE AFTER end_time;
