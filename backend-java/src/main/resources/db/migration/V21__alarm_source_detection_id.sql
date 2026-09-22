-- 消费端幂等：同一 detectionId 只落一条告警。NULL 允许多行，兼容旧消息。
ALTER TABLE alarm_event ADD COLUMN source_detection_id VARCHAR(36) NULL;

CREATE UNIQUE INDEX uk_alarm_source_detection
  ON alarm_event (source_detection_id);
