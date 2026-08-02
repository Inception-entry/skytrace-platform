SET @exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inspection_task'
      AND column_name = 'route_code'
);
SET @sql := IF(
    @exists = 0,
    'ALTER TABLE inspection_task ADD COLUMN route_code VARCHAR(64) NULL AFTER device_code',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
