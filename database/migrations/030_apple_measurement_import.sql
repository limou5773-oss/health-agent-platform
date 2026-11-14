USE health_agent;

ALTER TABLE measurement_import_batches
  ADD COLUMN import_format VARCHAR(20) NOT NULL DEFAULT 'CSV',
  ADD COLUMN skipped_rows INT NOT NULL DEFAULT 0;
