USE health_agent;

CREATE TEMPORARY TABLE sleep_report_versions (
  id BIGINT PRIMARY KEY,
  version INT NOT NULL
);

INSERT INTO sleep_report_versions (id, version)
SELECT id, ROW_NUMBER() OVER (
  PARTITION BY user_id, start_date, end_date
  ORDER BY created_at, id
)
FROM reports
WHERE report_type = 'SLEEP';

UPDATE reports r
JOIN sleep_report_versions v ON r.id = v.id
SET r.report_version = v.version;

DROP TEMPORARY TABLE sleep_report_versions;
