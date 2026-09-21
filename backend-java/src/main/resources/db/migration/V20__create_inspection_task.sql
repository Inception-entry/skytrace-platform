-- 空库不再依赖 deploy/mysql/init 才能有 inspection_task。
-- IF NOT EXISTS：已用 Docker init 建过表的环境只补索引。
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

CREATE INDEX idx_task_device_status_updated
  ON inspection_task (device_code, status, updated_at);

CREATE INDEX idx_task_route_code
  ON inspection_task (route_code);
