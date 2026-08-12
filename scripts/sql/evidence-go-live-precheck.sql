-- Evidence Center Phase 3 上线前数据库预检 SQL
--
-- 用途：
-- 1. 核对 Flyway 关键版本是否成功落库。
-- 2. 统计历史 content_hash 缺口。
-- 3. 预览归档/软删除/清理相关数据分布。
--
-- 说明：
-- - 本文件只包含只读查询，不修改任何数据。
-- - 生产 repair、回填、清理执行前，请先完成数据库备份。

-- 1) Flyway 关键迁移核查
SELECT
  installed_rank,
  version,
  description,
  type,
  script,
  checksum,
  installed_by,
  installed_on,
  success
FROM flyway_schema_history
WHERE version IN ('10', '12', '14', '15', '18')
ORDER BY installed_rank;

-- 2) 整体迁移历史概览
SELECT
  COUNT(*) AS total_migrations,
  SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successful_migrations,
  SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failed_migrations
FROM flyway_schema_history;

-- 3) 历史 content_hash 缺口统计
SELECT
  COUNT(*) AS missing_content_hash_count
FROM evidence_asset
WHERE content_hash IS NULL
   OR LENGTH(TRIM(content_hash)) = 0;

-- 4) 缺口按证据状态分布
SELECT
  COALESCE(archive_status, 'NULL') AS archive_status,
  deleted,
  COUNT(*) AS record_count
FROM evidence_asset
WHERE content_hash IS NULL
   OR LENGTH(TRIM(content_hash)) = 0
GROUP BY archive_status, deleted
ORDER BY archive_status, deleted;

-- 5) 归档状态整体分布
SELECT
  COALESCE(archive_status, 'NULL') AS archive_status,
  COUNT(*) AS record_count
FROM evidence_asset
GROUP BY archive_status
ORDER BY archive_status;

-- 6) 软删除与保留期数据分布
SELECT
  deleted,
  COUNT(*) AS record_count,
  MIN(deleted_at) AS earliest_deleted_at,
  MAX(deleted_at) AS latest_deleted_at
FROM evidence_asset
GROUP BY deleted
ORDER BY deleted;

-- 7) 具备“可能进入清理候选”基础特征的数据量
-- 说明：这里只做粗统计，不代表一定可删；真正清理还要校验归档包和 manifest 完整性。
SELECT
  COUNT(*) AS coarse_cleanup_candidate_count
FROM evidence_asset
WHERE deleted = 1
  AND archive_status = 'ARCHIVED'
  AND content_hash IS NOT NULL
  AND LENGTH(TRIM(content_hash)) > 0
  AND archive_batch_code IS NOT NULL
  AND archived_at IS NOT NULL
  AND deleted_at IS NOT NULL;

-- 8) 历史回填前抽样
SELECT
  evidence_code,
  task_code,
  alarm_event_code,
  object_key,
  archive_status,
  deleted,
  created_at
FROM evidence_asset
WHERE content_hash IS NULL
   OR LENGTH(TRIM(content_hash)) = 0
ORDER BY created_at ASC
LIMIT 20;
