USE health_agent;

CREATE TABLE heart_rate_samples (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, measured_at DATETIME NOT NULL,
 bpm DECIMAL(7,3) NOT NULL CHECK(bpm > 0),
 source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, note VARCHAR(1000) NOT NULL,
 corrected BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE KEY uk_heart_rate_sample(user_id,measured_at,source_name)
);
