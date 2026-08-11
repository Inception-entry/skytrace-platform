ALTER TABLE evidence_asset ADD COLUMN content_hash VARCHAR(128) NULL;
ALTER TABLE evidence_asset ADD COLUMN archive_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE evidence_asset ADD COLUMN archive_batch_code VARCHAR(64) NULL;
ALTER TABLE evidence_asset ADD COLUMN archived_at DATETIME NULL;

CREATE INDEX idx_evidence_archive_status_created_at
  ON evidence_asset (archive_status, created_at);
CREATE INDEX idx_evidence_content_hash
  ON evidence_asset (content_hash);
