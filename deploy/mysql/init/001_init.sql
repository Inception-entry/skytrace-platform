CREATE DATABASE IF NOT EXISTS skytrace_inspection
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
USE skytrace_inspection;
CREATE TABLE IF NOT EXISTS alarm_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_code VARCHAR(64) NOT NULL UNIQUE,
  device_code VARCHAR(64) NOT NULL,
  task_code VARCHAR(64),
  event_type VARCHAR(64) NOT NULL,
  weapon_type VARCHAR(64),
  confidence DECIMAL(5,4),
  latitude DECIMAL(10,7),
  longitude DECIMAL(10,7),
  image_url VARCHAR(512),
  video_url VARCHAR(512),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  event_time DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_code VARCHAR(64) NOT NULL UNIQUE,
  device_name VARCHAR(128) NOT NULL,
  device_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO device (device_code, device_name, device_type, status)
SELECT 'UAV-001', '一号无人机', 'UAV', 'OFFLINE'
WHERE NOT EXISTS (
  SELECT 1 FROM device WHERE device_code = 'UAV-001'
);

INSERT INTO device (device_code, device_name, device_type, status)
SELECT 'CAMERA-001', '一号固定摄像头', 'CAMERA', 'OFFLINE'
WHERE NOT EXISTS (
  SELECT 1 FROM device WHERE device_code = 'CAMERA-001'
);

CREATE TABLE IF NOT EXISTS inspection_route (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  route_code VARCHAR(64) NOT NULL UNIQUE,
  route_name VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  waypoints_json TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO inspection_route (route_code, route_name, description, waypoints_json)
SELECT 'ROUTE-001', '东区示例航线', '默认演示航线',
       '[{"lat":31.2304,"lng":121.4737,"alt":80},{"lat":31.2330,"lng":121.4800,"alt":90}]'
WHERE NOT EXISTS (
  SELECT 1 FROM inspection_route WHERE route_code = 'ROUTE-001'
);

CREATE TABLE IF NOT EXISTS inspection_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL UNIQUE,
  task_name VARCHAR(128) NOT NULL,
  device_code VARCHAR(64),
  route_code VARCHAR(64),
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  plan_start_time DATETIME,
  plan_end_time DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evidence_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  object_key VARCHAR(512) NOT NULL UNIQUE,
  bucket VARCHAR(128) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  original_filename VARCHAR(255),
  size_bytes BIGINT NOT NULL,
  task_code VARCHAR(64),
  alarm_event_code VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_evidence_task_code (task_code),
  INDEX idx_evidence_alarm_event_code (alarm_event_code)
);
