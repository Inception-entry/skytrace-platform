CREATE TABLE IF NOT EXISTS evidence_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  object_key VARCHAR(512) NOT NULL UNIQUE,
  bucket VARCHAR(128) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  original_filename VARCHAR(255),
  size_bytes BIGINT NOT NULL,
  task_code VARCHAR(64),
  alarm_event_code VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_evidence_task_code (task_code),
  INDEX idx_evidence_alarm_event_code (alarm_event_code)
);
