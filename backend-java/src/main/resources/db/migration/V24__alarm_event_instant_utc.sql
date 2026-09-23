-- v2 告警时刻。旧行留空，读取时由上海 event_time 推导。
ALTER TABLE alarm_event ADD COLUMN event_instant_utc VARCHAR(40) NULL;
