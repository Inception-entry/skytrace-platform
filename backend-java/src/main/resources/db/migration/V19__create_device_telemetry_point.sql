-- 任务级飞行遥测历史，用于事后回放。仅在设备存在关联的 RUNNING 任务时落库，
-- 避免把所有心跳期的坐标都无限期保留。
CREATE TABLE device_telemetry_point (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_code VARCHAR(64) NOT NULL,
  task_code VARCHAR(64) NOT NULL,
  latitude DECIMAL(10, 7) NOT NULL,
  longitude DECIMAL(10, 7) NOT NULL,
  altitude DECIMAL(8, 2) NULL,
  heading DECIMAL(6, 2) NULL,
  source VARCHAR(32) NULL,
  recorded_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL
);

CREATE INDEX idx_telemetry_task_recorded
  ON device_telemetry_point (task_code, recorded_at);

CREATE INDEX idx_telemetry_device_recorded
  ON device_telemetry_point (device_code, recorded_at);
