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
