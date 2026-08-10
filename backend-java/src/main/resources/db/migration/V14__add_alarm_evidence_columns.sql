ALTER TABLE alarm_event
  ADD COLUMN primary_evidence_code VARCHAR(64) NULL AFTER image_url,
  ADD COLUMN primary_video_evidence_code VARCHAR(64) NULL AFTER video_url;

CREATE INDEX idx_alarm_primary_evidence
  ON alarm_event (primary_evidence_code);