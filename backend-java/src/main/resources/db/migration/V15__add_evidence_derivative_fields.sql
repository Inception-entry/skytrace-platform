ALTER TABLE evidence_asset ADD COLUMN thumbnail_object_key VARCHAR(512) NULL;
ALTER TABLE evidence_asset ADD COLUMN poster_object_key VARCHAR(512) NULL;
ALTER TABLE evidence_asset ADD COLUMN derivative_status VARCHAR(32) NOT NULL DEFAULT 'NONE';

CREATE INDEX idx_evidence_derivative_status
  ON evidence_asset (derivative_status);
