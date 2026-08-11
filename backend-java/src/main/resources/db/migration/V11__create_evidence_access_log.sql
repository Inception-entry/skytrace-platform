CREATE TABLE IF NOT EXISTS evidence_access_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  evidence_id BIGINT NOT NULL,
  evidence_code VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  actor_id VARCHAR(128) NOT NULL,
  username VARCHAR(128) NOT NULL,
  roles VARCHAR(256) NOT NULL,
  request_id VARCHAR(128) NULL,
  client_ip VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_evidence_access_code_created_at (evidence_code, created_at),
  INDEX idx_evidence_access_actor_created_at (actor_id, created_at)
);