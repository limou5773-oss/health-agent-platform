USE health_agent;

CREATE TABLE import_template_notes (
 kind ENUM('SLEEP','STEPS') PRIMARY KEY, instructions VARCHAR(1000) NOT NULL
);
CREATE TABLE import_issue_counts (
 kind ENUM('SLEEP','STEPS') NOT NULL, batch_id BIGINT NOT NULL,
 reason VARCHAR(40) NOT NULL, row_count INT NOT NULL,
 PRIMARY KEY(kind,batch_id,reason)
);
CREATE TABLE rejected_imports (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, kind ENUM('SLEEP','STEPS') NOT NULL,
 user_id BIGINT NOT NULL, error_code VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_rejected_import_kind(kind,id)
);
