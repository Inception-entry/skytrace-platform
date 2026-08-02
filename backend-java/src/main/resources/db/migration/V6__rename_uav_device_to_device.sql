-- Align legacy uav_device with domain table naming (inspection_task / alarm_event style).
-- No-op when the table was already created as device by a fresh MySQL init.

SET @has_old := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'uav_device'
);
SET @has_new := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'device'
);

SET @rename_sql := IF(
    @has_old > 0 AND @has_new = 0,
    'RENAME TABLE uav_device TO device',
    'SELECT 1'
);
PREPARE rename_stmt FROM @rename_sql;
EXECUTE rename_stmt;
DEALLOCATE PREPARE rename_stmt;

SET @has_old_after := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'uav_device'
);
SET @has_new_after := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'device'
);

SET @merge_sql := IF(
    @has_old_after > 0 AND @has_new_after > 0,
    'INSERT INTO device (device_code, device_name, device_type, status, created_at, updated_at)
     SELECT device_code, device_name, device_type, status, created_at, updated_at
     FROM uav_device src
     WHERE NOT EXISTS (
         SELECT 1 FROM device dst WHERE dst.device_code = src.device_code
     )',
    'SELECT 1'
);
PREPARE merge_stmt FROM @merge_sql;
EXECUTE merge_stmt;
DEALLOCATE PREPARE merge_stmt;

SET @drop_sql := IF(
    @has_old_after > 0 AND @has_new_after > 0,
    'DROP TABLE uav_device',
    'SELECT 1'
);
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;
