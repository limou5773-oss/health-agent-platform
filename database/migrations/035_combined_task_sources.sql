USE health_agent;

ALTER TABLE agent_tasks
  ADD COLUMN step_source VARCHAR(100),
  ADD COLUMN workout_source VARCHAR(100),
  ADD COLUMN weight_source VARCHAR(100),
  ADD COLUMN heart_rate_source VARCHAR(100);
