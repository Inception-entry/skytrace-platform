# 证据哈希回填、归档清理与压测 Runbook

> 适用范围：Evidence Center Phase 3。
> 物理清理不可逆，生产环境必须按“回填 -> 预览 -> 小批量清理 -> 审计复核”的顺序执行。

## 1. 当前安全默认值

| 能力 | 默认值 | 含义 |
| --- | --- | --- |
| 历史哈希定时回填 | `false` | 不会在应用启动后自动扫描 MinIO |
| 回填批次 | `25` | 每轮最多处理 25 条候选记录 |
| 回填失败重试间隔 | `24h` | 坏对象不会持续占满任务 |
| 归档后自动清理 | `false` | 默认不执行任何物理删除 |
| 清理演练 | `true` | 即使开启 Scheduler，也只返回候选 |
| 归档后保留期 | `90d` | 归档时间和软删除时间都必须超过 90 天 |
| 清理批次 | `20` | 每轮最多清理 20 条记录 |
| 清理时间 | 每天 `03:30` | 使用应用进程时区 |

生产环境第一次部署时不要同时打开回填和正式清理。先保持上述默认值，完成数据库迁移、
权限检查、MinIO 校验和 dry-run 后再逐项启用。

## 2. 哪些证据允许物理删除

一条证据只有同时满足以下条件才会进入候选：

1. `deleted=true`，用户已经执行过软删除。
2. `archive_status=ARCHIVED`，不能删除仍在线或归档中的证据。
3. `content_hash` 非空，数据库仍能证明原件内容摘要。
4. `archive_batch_code` 非空，可追溯到具体归档任务。
5. `archived_at` 和 `deleted_at` 都早于保留期截止时间。
6. 对应 `evidence_archive_job.status=COMPLETED`。
7. 归档 ZIP、独立 `manifest.json` 在 MinIO 中都存在。
8. 本清理批次从 MinIO 流式重算的 ZIP SHA-256 与 `package_content_hash` 一致。
9. 独立 `manifest.json` 与 ZIP 首项中的 `manifest.json` 字节完全一致。
10. 当前证据的 `evidenceCode`、`contentHash`、`sizeBytes` 和归档路径均与 manifest 唯一条目匹配。

任一条件不满足都会阻断删除。旧版本归档任务若没有 `package_content_hash`，默认不可信，
不能作为物理删除依据。候选查询后的原子认领 UPDATE 会再次检查软删除、状态、双时间、
归档批次和哈希条件，避免并发恢复或数据变化穿过查询窗口。

## 3. 实际删除什么、保留什么

正式清理会删除：

- 原始对象 `object_key`
- 图片缩略图 `thumbnail_object_key`
- 视频封面 `poster_object_key`

正式清理不会删除：

- `archives/<jobCode>/<jobCode>.zip`
- `archives/<jobCode>/manifest.json`
- MySQL 中的证据记录
- `evidence_code`、`content_hash`、`archive_batch_code` 和历史时间字段

删除成功后证据进入 `PURGED`，数据库记录作为墓碑和追溯索引继续保留。存储层还会拒绝
任何以 `archives/` 开头的物理删除请求，避免清理代码误删归档产物。`PURGING` 期间会
临时禁止恢复，`PURGED` 证据则永久不能通过恢复接口重新变成在线对象。

状态变化如下：

```text
ACTIVE -> ARCHIVED -> PURGING -> PURGED
             ^           |
             |-----------|
          失败或超时后释放认领
```

## 4. 谁执行、谁能执行

### 自动执行者

`EvidenceMaintenanceScheduler` 负责定时回填与清理。单 JVM 使用 `AtomicBoolean` 防止同类
任务重叠，多实例之间依靠数据库原子状态转换认领单条记录。

### 手动执行者

只有 Keycloak `ADMIN` 角色可以访问 `/api/admin/evidence-maintenance/**`。`OPERATOR` 和
`VIEWER` 都不能触发回填或清理。正式清理还必须同时提交：

```text
dryRun=false
confirmation=PURGE_ARCHIVED_EVIDENCE
```

缺少确认串、确认串错误或 `dryRun` 不是严格的 `true/false` 时，请求会失败，不会降级成
物理删除。

如果要给业务、研发、运维做正式评审，建议同时参照：
[归档后清理策略](./retention-policy.md)

## 5. 管理接口

以下是经 Gateway 和 Node BFF 暴露的路径：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/admin/evidence-maintenance/policy` | 查看当前生效策略 |
| `POST` | `/api/admin/evidence-maintenance/hash-backfill?batchSize=10` | 手动回填一小批历史哈希 |
| `GET` | `/api/admin/evidence-maintenance/cleanup-preview?batchSize=20` | 无副作用预览候选 |
| `POST` | `/api/admin/evidence-maintenance/cleanup?dryRun=true&batchSize=20` | 通过 POST 执行演练 |
| `POST` | `/api/admin/evidence-maintenance/cleanup?dryRun=false&batchSize=5&confirmation=PURGE_ARCHIVED_EVIDENCE` | 正式小批量清理 |

手动回填不依赖 Scheduler 开关，便于先用 `batchSize=1/5/10` 灰度。接口批次有硬上限：
回填 500 条，清理 200 条。

## 6. 推荐上线步骤

### 第一步：迁移与备份

1. 备份 MySQL 和证据桶。
2. 部署 V18，确认 `flyway_schema_history` 中版本 18 为成功。
3. 不要修改已经执行过的 V1-V17，也不要为了上线临时关闭生产 Flyway 校验。
4. 确认 Java、Node、Gateway、Temporal、MinIO 均健康。

如果环境已经暴露出 `V10/V12/V14/V15` checksum 漂移，先按下面顺序收口：

1. 冻结上线窗口，避免同一时间再改历史 migration。
2. 备份目标库。
3. 在预发库执行一次只读预检：
   [evidence-go-live-precheck.sql](../../scripts/sql/evidence-go-live-precheck.sql)
4. 确认漂移只存在于历史已执行版本，且仓库中的 `V10/V12/V14/V15` 不再继续改动。
5. 使用与生产同版本的应用包验证：不关闭 `validate` 时是否仅剩 checksum 问题。
6. 若确认只是历史 checksum 与当前仓库不一致，而 SQL 语义已与仓库对齐，则在预发先演练
   `flyway repair`。
7. 预发 repair 成功后，再对生产执行相同流程。

生产是否允许 repair 由运维和 DBA 共同审批；如果无法确认漂移来源，不要直接上线。

### 第二步：查看策略和历史缺口

先调用 policy 接口，再统计历史缺口：

```sql
SELECT COUNT(*)
FROM evidence_asset
WHERE content_hash IS NULL OR LENGTH(TRIM(content_hash)) = 0;
```

### 第三步：小批量回填

1. 先手动执行 `batchSize=5`。
2. 检查响应中的 `selected/claimed/succeeded/failed/failedEvidenceCodes`。
3. 查询 `hash_backfill_error`，先修复对象不存在、桶错误或权限错误。
4. 小批量稳定后再设置 `EVIDENCE_HASH_BACKFILL_ENABLED=true`。

回填通过固定输入流计算 SHA-256，不把大对象完整读入内存。失败记录写入
`hash_backfill_attempted_at/hash_backfill_error`，超过退避时间后才再次成为候选，不会阻塞
同批其他对象。

### 第四步：只做清理预览

至少连续观察一个完整保留期策略周期。核对每条候选的证据编号、归档批次、软删除时间、
归档时间和业务审批记录。此阶段保持：

```dotenv
EVIDENCE_CLEANUP_ENABLED=true
EVIDENCE_CLEANUP_DRY_RUN=true
```

### 第五步：正式清理

1. 再做一次数据库和对象存储备份。
2. 先手动执行 `batchSize=1` 且提供确认串。
3. 验证原件已不存在、归档 ZIP 仍存在、数据库状态为 `PURGED`。
4. 检查 STARTED 和 SUCCESS 审计。
5. 再逐步提高到 5、10、20；不要第一次就使用接口硬上限。

## 7. 审计与失败恢复

HTTP 管理操作会进入通用审计：

- `EVIDENCE_HASH_BACKFILL`
- `EVIDENCE_CLEANUP_EXECUTE`

每条物理清理还会以系统身份写入独立审计：

- `action=EVIDENCE_PHYSICAL_PURGE, outcome=STARTED`
- `action=EVIDENCE_PHYSICAL_PURGE, outcome=SUCCESS`
- `action=EVIDENCE_PHYSICAL_PURGE, outcome=FAILURE`

删除前必须先成功提交 STARTED 审计；写不进去就不删除。若删除过程中失败，记录恢复到
`ARCHIVED` 并保存 `purge_error`。若进程在 `PURGING` 中断，超过
`EVIDENCE_CLEANUP_STALE_CLAIM_HOURS` 后由下一轮释放认领。MinIO 删除是幂等调用，因此
部分对象已经删除时可以安全重试，最终仍以归档包为恢复来源。

## 8. 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `EVIDENCE_HASH_BACKFILL_ENABLED` | `false` | 开启定时回填 |
| `EVIDENCE_HASH_BACKFILL_BATCH_SIZE` | `25` | 回填批次 |
| `EVIDENCE_HASH_BACKFILL_RETRY_HOURS` | `24` | 失败退避小时 |
| `EVIDENCE_HASH_BACKFILL_DELAY_MS` | `300000` | 固定轮询间隔 |
| `EVIDENCE_CLEANUP_ENABLED` | `false` | 开启清理调度 |
| `EVIDENCE_CLEANUP_DRY_RUN` | `true` | 是否只演练 |
| `EVIDENCE_CLEANUP_RETENTION_DAYS` | `90` | 归档及软删除保留天数 |
| `EVIDENCE_CLEANUP_BATCH_SIZE` | `20` | 清理批次 |
| `EVIDENCE_CLEANUP_CRON` | `0 30 3 * * *` | 清理 cron |
| `EVIDENCE_CLEANUP_STALE_CLAIM_HOURS` | `6` | 超时认领释放时间 |
| `MINIO_CONNECT_TIMEOUT` | `5s` | 连接失败快速交给 Temporal |
| `MINIO_READ_TIMEOUT` | `5m` | 大对象读取超时 |
| `MINIO_WRITE_TIMEOUT` | `5m` | 大对象写入超时 |
| `MINIO_ARCHIVE_TEMP_DIR` | JVM 临时目录 | 磁盘 ZIP 工作目录 |

## 9. 真实归档压测

脚本：[`../../scripts/ci/verify-evidence-archive.sh`](../../scripts/ci/verify-evidence-archive.sh)

如果需要把“压测”和“前端/BFF/权限/失败恢复联调”一起收口，建议同时使用：
[真实环境联调与验收清单](./integration-acceptance-checklist.md)
[联调当天操作单](./integration-day-playbook.md)

它会创建独立任务，上传真实对象，创建归档任务，轮询 Temporal，下载 ZIP 和 manifest，
并验证：

- 任务内文件数量
- ZIP 自身 `packageContentHash`
- 独立 manifest 与 ZIP 内 manifest 一致
- `checksums.sha256` 与 manifest 文件集合一致
- ZIP 中每个文件的 SHA-256
- 浏览器可达的预签名下载链接
- 可选 MinIO 故障后的 Temporal `attempt > 1`

常规压测：

```bash
KEYCLOAK_CLIENT_SECRET='***' \
ARCHIVE_FILE_COUNT=50 \
ARCHIVE_FILE_SIZE_MB=4 \
./scripts/ci/verify-evidence-archive.sh
```

故障恢复压测：

```bash
KEYCLOAK_CLIENT_SECRET='***' \
ARCHIVE_FILE_COUNT=2 \
ARCHIVE_FILE_SIZE_MB=1 \
ARCHIVE_MINIO_OUTAGE_SECONDS=10 \
./scripts/ci/verify-evidence-archive.sh
```

故障注入只适用于隔离的本地/测试环境，禁止对生产 MinIO 执行。

## 10. 2026-08-11 本地实测记录

| 场景 | 输入 | 结果 |
| --- | --- | --- |
| 大文件归档 | 4 个文件，每个 8 MiB | 生成 33,566,089 字节 ZIP，约 3 秒完成，全部哈希核验通过 |
| 常规后端压测 | 20 个文件，每个 4 MiB | `TASK_CODE=PRESSURE-JAVA-1786436519-16382`，`JOB_CODE=AR-20260811-93CCD2`，总计 80 MiB，上传约 2 秒，ZIP 83,915,893 字节，manifest 7,928 字节，下载通过 |
| 下载链接 | ZIP + manifest | 经前端 Nginx 的预签名 URL 均可下载 |
| 故障恢复 | 1 MiB 文件，MinIO 停止 10 秒 | Temporal 最大 Activity attempt 为 2，恢复后归档与下载通过 |
| 故障恢复后端压测 | 2 个文件，每个 1 MiB，MinIO 停止 10 秒 | `TASK_CODE=PRESSURE-RECOVERY-1786436567-23628`，`JOB_CODE=AR-20260811-FD1C5C`，ZIP 2,098,850 字节，恢复后下载通过 |
| 真实库历史缺口 | V18 迁移后统计 | 2 条 `content_hash` 为空；未擅自执行回填 |

说明：

1. 该本地数据库在本次开发前已有 `V10/V12/V14/V15` 校验和漂移。
2. 本轮已在本地 `flyway_schema_history` 中修正这 4 个历史 checksum，使当前仓库脚本和
   本地元数据重新对齐；随后 `backend-java` 于 2026-08-11 16:04:01 成功验证 18 个
   migration 并恢复启动。
3. 以上 repair 仅用于恢复本地验证环境，不代表生产可以直接照抄执行；生产仍必须先完成
   备份、预发演练和审批。
4. 本轮还在本地临时关闭鉴权的前提下验证了真实后端压测；压测结束后已恢复 Gateway 和
   Java 的安全配置，未登录访问重新回到 `401` 受保护状态。

## 11. 当前边界

- `CASE` 仍是保留枚举，接口当前只接受 `TASK/ALARM`。
- 归档 ZIP 使用本地临时磁盘，仍需监控磁盘配额和进程强杀后的残留文件。
- 本轮没有对真实业务证据执行物理清理，只通过单元测试、权限测试和 dry-run 设计验证。
- Object Lock/WORM、外部可信时间戳和电子签章仍属于后续合规增强。
