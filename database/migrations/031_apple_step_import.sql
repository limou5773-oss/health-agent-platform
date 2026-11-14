USE health_agent;

ALTER TABLE step_import_batches
  ADD COLUMN import_format VARCHAR(20) NOT NULL DEFAULT 'CSV' AFTER status,
  ADD COLUMN skipped_rows INT NOT NULL DEFAULT 0 AFTER import_format;
