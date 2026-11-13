USE health_agent;

CREATE TABLE measurement_import_batches (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 metric ENUM('WEIGHT','HEART_RATE') NOT NULL, status ENUM('PREVIEW','CONFIRMED') NOT NULL,
 total_rows INT NOT NULL, duplicate_rows INT NOT NULL, inserted_rows INT NOT NULL DEFAULT 0,
 first_measurement DATETIME NOT NULL, last_measurement DATETIME NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, confirmed_at DATETIME NULL,
 KEY ix_measurement_import_history(user_id,metric,id)
);
CREATE TABLE measurement_import_rows (
 batch_id BIGINT NOT NULL, row_number INT NOT NULL, measured_at DATETIME NOT NULL,
 measurement_value DECIMAL(7,3) NOT NULL, source_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
 note VARCHAR(1000) NOT NULL,
 PRIMARY KEY(batch_id,row_number)
);
