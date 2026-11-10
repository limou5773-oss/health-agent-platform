-- Run after the applicable earlier migrations. The application does not run this file.
USE health_agent;

ALTER TABLE sleep_records
 ADD COLUMN source_app VARCHAR(20) NOT NULL DEFAULT 'OTHER',
 ADD COLUMN device_brand VARCHAR(20) NOT NULL DEFAULT 'OTHER',
 ADD COLUMN device_type VARCHAR(20) NOT NULL DEFAULT 'OTHER',
 ADD COLUMN device_model VARCHAR(80) NOT NULL DEFAULT '';

ALTER TABLE import_batches
 ADD COLUMN source_app VARCHAR(20) NOT NULL DEFAULT 'OTHER',
 ADD COLUMN device_brand VARCHAR(20) NOT NULL DEFAULT 'OTHER',
 ADD COLUMN device_type VARCHAR(20) NOT NULL DEFAULT 'OTHER',
 ADD COLUMN device_model VARCHAR(80) NOT NULL DEFAULT '';

-- The original Apple-only endpoint supplied these batches, but did not identify hardware.
UPDATE import_batches SET source_app = 'APPLE_HEALTH' WHERE import_format = 'APPLE_HEALTH';
