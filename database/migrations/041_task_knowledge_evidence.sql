USE health_agent;

ALTER TABLE agent_tasks ADD COLUMN knowledge_evidence_json JSON;
