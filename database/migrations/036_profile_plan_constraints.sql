USE health_agent;

ALTER TABLE profiles
  ADD COLUMN available_time VARCHAR(500) NOT NULL DEFAULT '',
  ADD COLUMN limitations VARCHAR(500) NOT NULL DEFAULT '',
  ADD COLUMN updated_at DATETIME(6);
