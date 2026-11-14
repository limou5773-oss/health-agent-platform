USE health_agent;

CREATE TABLE health_goals (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
 metric ENUM('SLEEP','STEPS','WORKOUT','MEAL','WATER','WEIGHT','HEART_RATE') NOT NULL,
 direction ENUM('AT_LEAST','AT_MOST') NOT NULL, target_value DECIMAL(12,3) NOT NULL,
 preferences VARCHAR(500) NOT NULL,
 KEY ix_health_goals_user(user_id,end_date),
 CHECK(end_date >= start_date AND DATEDIFF(end_date,start_date) < 366),
 CHECK(target_value >= 0)
);

ALTER TABLE plans
  ADD COLUMN goal_id BIGINT,
  ADD KEY ix_plan_goal(user_id,goal_id);

ALTER TABLE plan_revisions ADD COLUMN confirmed_at DATETIME(6);
UPDATE plan_revisions SET confirmed_at=created_at WHERE version=1 AND status='CONFIRMED';
