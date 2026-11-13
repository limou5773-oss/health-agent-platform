USE health_agent;

ALTER TABLE plan_items ADD COLUMN missed_reason VARCHAR(500) NOT NULL DEFAULT '';
