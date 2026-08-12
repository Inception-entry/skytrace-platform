-- 历史哈希回填需要记录最近尝试时间，避免永久失败对象阻塞后续批次。
ALTER TABLE evidence_asset
  ADD COLUMN hash_backfill_attempted_at DATETIME NULL;
ALTER TABLE evidence_asset
  ADD COLUMN hash_backfill_error VARCHAR(512) NULL;

-- 物理内容清理保留数据库墓碑，只记录认领、完成时间和最近失败原因。
ALTER TABLE evidence_asset
  ADD COLUMN purge_started_at DATETIME NULL;
ALTER TABLE evidence_asset
  ADD COLUMN purged_at DATETIME NULL;
ALTER TABLE evidence_asset
  ADD COLUMN purge_error VARCHAR(512) NULL;

-- 归档包哈希用于在删除原始对象前验证 MinIO 中的归档产物没有变化。
ALTER TABLE evidence_archive_job
  ADD COLUMN package_content_hash VARCHAR(128) NULL;
ALTER TABLE evidence_archive_job
  ADD COLUMN package_verified_at DATETIME NULL;

-- 回填任务优先选择从未尝试或已到重试时间的记录。
CREATE INDEX idx_evidence_hash_backfill_attempt
  ON evidence_asset (content_hash, hash_backfill_attempted_at);

-- 清理任务按状态、软删除标记和两个保留期时间点筛选候选记录。
CREATE INDEX idx_evidence_purge_candidate
  ON evidence_asset (archive_status, deleted, archived_at, deleted_at);
