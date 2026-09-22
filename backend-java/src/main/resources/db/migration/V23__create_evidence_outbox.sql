-- 证据落库与 Temporal/MinIO 副作用拆开：同事务写入 outbox，提交后再投。
CREATE TABLE IF NOT EXISTS evidence_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  aggregate_code VARCHAR(512) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at DATETIME NULL,
  UNIQUE KEY uk_evidence_outbox_aggregate_kind (aggregate_code, kind)
);

CREATE INDEX idx_evidence_outbox_drain
  ON evidence_outbox (status, available_at, id);
