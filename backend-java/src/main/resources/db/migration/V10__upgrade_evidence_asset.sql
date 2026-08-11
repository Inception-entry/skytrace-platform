ALTER TABLE evidence_asset ADD COLUMN evidence_code VARCHAR(64) NULL;
ALTER TABLE evidence_asset ADD COLUMN asset_type VARCHAR(32) NOT NULL DEFAULT 'IMAGE';
ALTER TABLE evidence_asset ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL_UPLOAD';
ALTER TABLE evidence_asset ADD COLUMN device_code VARCHAR(64) NULL;
ALTER TABLE evidence_asset ADD COLUMN uploaded_by VARCHAR(128) NULL;
ALTER TABLE evidence_asset ADD COLUMN uploaded_by_name VARCHAR(128) NULL;
ALTER TABLE evidence_asset ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE evidence_asset ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE evidence_asset ADD COLUMN deleted_by VARCHAR(128) NULL;
ALTER TABLE evidence_asset ADD COLUMN deleted_by_name VARCHAR(128) NULL;

UPDATE evidence_asset
SET evidence_code = CONCAT('EV-LEGACY-', LPAD(id, 8, '0'))
WHERE evidence_code IS NULL;

ALTER TABLE evidence_asset MODIFY COLUMN evidence_code VARCHAR(64) NOT NULL;
ALTER TABLE evidence_asset ADD CONSTRAINT uk_evidence_asset_code UNIQUE (evidence_code);

CREATE INDEX idx_evidence_created_at ON evidence_asset (created_at);
CREATE INDEX idx_evidence_device_code ON evidence_asset (device_code);
CREATE INDEX idx_evidence_asset_type ON evidence_asset (asset_type);
CREATE INDEX idx_evidence_source_type ON evidence_asset (source_type);
CREATE INDEX idx_evidence_deleted_created_at ON evidence_asset (deleted, created_at);
