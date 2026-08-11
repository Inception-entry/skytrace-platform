CREATE TABLE evidence_archive_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL,
  scope_type VARCHAR(32) NOT NULL,
  scope_value VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  output_bucket VARCHAR(128) NULL,
  output_object_key VARCHAR(512) NULL,
  manifest_object_key VARCHAR(512) NULL,
  total_files INT NOT NULL DEFAULT 0,
  total_bytes BIGINT NOT NULL DEFAULT 0,
  created_by VARCHAR(128) NOT NULL,
  created_by_name VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  error_message VARCHAR(512) NULL,
  UNIQUE KEY uk_evidence_archive_job_code (job_code)
);
