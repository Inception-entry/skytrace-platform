ALTER TABLE evidence_asset
  ADD COLUMN thumbnail_object_key VARCHAR(512) NULL AFTER original_filename,
  ADD COLUMN poster_object_key VARCHAR(512) NULL AFTER thumbnail_object_key,
  ADD COLUMN derivative_status VARCHAR(32) NOT NULL DEFAULT 'NONE' AFTER poster_object_key;

CREATE INDEX idx_evidence_derivative_status
  ON evidence_asset (derivative_status);