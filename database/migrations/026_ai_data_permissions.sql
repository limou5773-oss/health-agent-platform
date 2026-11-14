USE health_agent;

ALTER TABLE profiles
  ADD COLUMN ai_sleep BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN ai_activity BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN ai_weight BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN ai_heart_rate BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN ai_diary BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN ai_consent_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE agent_tasks ADD COLUMN ai_consent_version BIGINT NOT NULL DEFAULT 0;

-- Existing AI checkpoints may contain profile weight or other data from before opt-in.
UPDATE agent_tasks SET ai_consent_version=-1
WHERE template_id IS NULL AND status NOT IN ('COMPLETED','CANCELLED');
