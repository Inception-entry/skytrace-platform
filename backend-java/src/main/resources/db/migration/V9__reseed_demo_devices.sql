-- 恢复演示设备种子（幂等：已存在则跳过）
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
