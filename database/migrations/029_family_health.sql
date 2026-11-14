USE health_agent;

CREATE TABLE family_grants (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 metrics_json JSON NOT NULL, report_ids_json JSON NOT NULL, accepted BOOLEAN NOT NULL DEFAULT FALSE,
 expires_at DATETIME NOT NULL, UNIQUE KEY uk_family_pair(owner_id,member_id),
 KEY ix_family_member(member_id,id)
);
