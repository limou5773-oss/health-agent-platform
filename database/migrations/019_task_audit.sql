USE health_agent;

ALTER TABLE agent_tasks
 ADD COLUMN confirmed_at DATETIME(6),
 ADD COLUMN feedback ENUM('HELPFUL','NOT_HELPFUL'),
 ADD COLUMN feedback_at DATETIME(6);
