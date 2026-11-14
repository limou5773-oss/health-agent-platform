USE health_agent;

ALTER TABLE agent_tasks
  ADD COLUMN parent_task_id CHAR(36),
  ADD KEY ix_agent_task_parent(user_id,parent_task_id);
