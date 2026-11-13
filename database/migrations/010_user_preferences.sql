USE health_agent;

CREATE TABLE user_preferences (
 user_id BIGINT NOT NULL,
 kind ENUM('GOAL','EXERCISE','ROUTINE','REMINDER') NOT NULL,
 content VARCHAR(500) NOT NULL,
 PRIMARY KEY(user_id,kind)
);
