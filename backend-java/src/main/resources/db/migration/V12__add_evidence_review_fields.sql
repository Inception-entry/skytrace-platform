ALTER TABLE evidence_asset ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE evidence_asset ADD COLUMN review_comment VARCHAR(512) NULL;
ALTER TABLE evidence_asset ADD COLUMN reviewed_at DATETIME NULL;
ALTER TABLE evidence_asset ADD COLUMN reviewed_by VARCHAR(128) NULL;
ALTER TABLE evidence_asset ADD COLUMN reviewed_by_name VARCHAR(128) NULL;
ALTER TABLE evidence_asset ADD COLUMN remark VARCHAR(512) NULL;
ALTER TABLE evidence_asset ADD COLUMN analysis_id VARCHAR(64) NULL;

CREATE INDEX idx_evidence_review_status_created_at
  ON evidence_asset (review_status, created_at);
