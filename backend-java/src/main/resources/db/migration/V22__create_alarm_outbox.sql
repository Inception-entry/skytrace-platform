-- 告警落库与 Temporal/实时投递拆开：同事务写入 outbox，提交后再投。
CREATE TABLE IF NOT EXISTS alarm_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_code VARCHAR(64) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at DATETIME NULL,
  UNIQUE KEY uk_alarm_outbox_event_kind (event_code, kind)
);

CREATE INDEX idx_alarm_outbox_drain
  ON alarm_outbox (status, available_at, id);
