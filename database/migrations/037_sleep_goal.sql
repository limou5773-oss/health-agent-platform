USE health_agent;

ALTER TABLE profiles
  ADD COLUMN sleep_goal_minutes SMALLINT CHECK(sleep_goal_minutes BETWEEN 1 AND 1440);
