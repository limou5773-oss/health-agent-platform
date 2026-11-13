USE health_agent;

CREATE TABLE plan_templates (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, version INT NOT NULL,
 category ENUM('SLEEP','EXERCISE','WATER') NOT NULL, title VARCHAR(200) NOT NULL,
 instructions VARCHAR(4000) NOT NULL, days INT NOT NULL, enabled BOOLEAN NOT NULL,
 items_json JSON NOT NULL, CHECK(days BETWEEN 1 AND 31)
);

ALTER TABLE agent_tasks ADD COLUMN template_id BIGINT, ADD COLUMN template_version INT;
