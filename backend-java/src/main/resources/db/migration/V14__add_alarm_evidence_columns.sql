-- Ensure base table exists for environments without MySQL init (H2/CI).
CREATE TABLE IF NOT EXISTS alarm_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_code VARCHAR(64) NOT NULL UNIQUE,
  device_code VARCHAR(64) NOT NULL,
  task_code VARCHAR(64),
  event_type VARCHAR(64) NOT NULL,
  weapon_type VARCHAR(64),
  confidence DECIMAL(5,4),
  latitude DECIMAL(10,7),
  longitude DECIMAL(10,7),
  image_url VARCHAR(512),
  video_url VARCHAR(512),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  event_time DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE alarm_event ADD COLUMN primary_evidence_code VARCHAR(64) NULL;
ALTER TABLE alarm_event ADD COLUMN primary_video_evidence_code VARCHAR(64) NULL;

CREATE INDEX idx_alarm_primary_evidence
  ON alarm_event (primary_evidence_code);
