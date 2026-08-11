CREATE TABLE evidence_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  color VARCHAR(32) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_evidence_tag_name (name)
);

CREATE TABLE evidence_tag_rel (
  evidence_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (evidence_id, tag_id),
  INDEX idx_evidence_tag_rel_tag_id (tag_id)
);

INSERT INTO evidence_tag (name, color) VALUES
  ('可疑目标', '#C45C26'),
  ('已确认', '#2F6F4E'),
  ('误报', '#6B7280'),
  ('待复核', '#B45309');