ALTER TABLE evidence_asset
  ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER source_type,
  ADD COLUMN review_comment VARCHAR(512) NULL AFTER review_status,
  ADD COLUMN reviewed_at DATETIME NULL AFTER review_comment,
  ADD COLUMN reviewed_by VARCHAR(128) NULL AFTER reviewed_at,
  ADD COLUMN reviewed_by_name VARCHAR(128) NULL AFTER reviewed_by,
  ADD COLUMN remark VARCHAR(512) NULL AFTER reviewed_by_name,
  ADD COLUMN analysis_id VARCHAR(64) NULL AFTER remark;

CREATE INDEX idx_evidence_review_status_created_at
  ON evidence_asset (review_status, created_at);