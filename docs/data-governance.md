# SkyTrace 数据治理手册

面向值班与发布工程师：业务数据（MySQL）、证据对象（MinIO）、知识库向量（Qdrant）
的备份、生命周期与重建流程。实现脚本见 `scripts/`，值班速查仍以 [ops.md](./ops.md) 为准。

## 1. 总览

| 数据 | 存储 | 真相源 | 丢失影响 | 恢复手段 |
| --- | --- | --- | --- | --- |
| 业务库（设备/任务/航线/告警/证据元数据/遥测点） | MySQL `skytrace_inspection` | DB | 业务不可用 | `mysql-backup.sh` / `restore-backup.sh` |
| 证据二进制 | MinIO `skytrace-evidence` | 对象存储 | 证据打不开 | 版本控制 + 跨区复制；**不能只靠 DB** |
| MySQL 备份文件 | MinIO `skytrace-backups` | 备份桶 | 无法按点恢复 | 提高保留天数 / 异地拷贝 |
| 知识库向量 | Qdrant `AI_QDRANT_COLLECTION` | 向量库 | RAG 检索失败 | 删 collection 后从文档重新入库 |
| Keycloak | MySQL `keycloak`（同实例或独立） | DB | 无法登录 | 与业务库一并 dump 或单独备份 |
| Temporal 状态 | 同 MySQL（生产） | DB | 工作流卡住 | 随 MySQL 备份 |

原则：

1. **元数据与对象分开备份**——证据表只有 `object_key`，恢复 DB 后必须保证 MinIO 对象仍在。
2. **可重建 vs 不可重建**——Qdrant 可由文档重算；业务 DB / 证据对象不可从别处重算。
3. **先演练再依赖**——每季度至少一次恢复演练。

---

## 2. MySQL 备份策略

### 2.1 日常备份

脚本：[`scripts/mysql-backup.sh`](../scripts/mysql-backup.sh)

```bash
# 从仓库根或 /opt/skytrace 执行；读取 deploy/.env
./scripts/mysql-backup.sh
```

流程：`mysqldump --single-transaction` → gzip → 上传 MinIO
`${BACKUP_BUCKET}/${MYSQL_DATABASE}/<timestamp>.sql.gz` → 按
`BACKUP_RETAIN_DAYS`（默认 7）清理旧对象。

| 变量 | 默认 | 含义 |
| --- | --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1` / `3307` | 宿主机访问业务库 |
| `MYSQL_DATABASE` | `skytrace_inspection` | 业务库名 |
| `BACKUP_BUCKET` | `skytrace-backups` | 备份桶 |
| `BACKUP_RETAIN_DAYS` | `7` | 对象保留天数 |
| `MINIO_*` | 见 `.env.example` | 上传凭证与端点 |

### 2.2 生产节奏（建议）

| 项 | 建议值 |
| --- | --- |
| 频率 | 每日 02:00（低峰） |
| RPO | ≤ 24h（与频率一致） |
| RTO | ≤ 2h（含恢复演练熟练度） |
| 保留 | 本地/MinIO 7～14 天；异地或冷存再留 30 天 |
| 触发 | systemd timer 或 cron：`0 2 * * * /opt/skytrace/scripts/mysql-backup.sh` |

同时 dump Keycloak 库（若同实例）：

```bash
mysqldump --single-transaction --routines --triggers keycloak \
  | gzip > /tmp/keycloak-$(date -u +%Y%m%dT%H%M%SZ).sql.gz
# 上传到同一 BACKUP_BUCKET 的 keycloak/ 前缀
```

### 2.3 恢复

脚本：[`scripts/restore-backup.sh`](../scripts/restore-backup.sh)

```bash
./scripts/restore-backup.sh --list
RESTORE_FILE=skytrace_inspection/20260812T020000Z.sql.gz ./scripts/restore-backup.sh
```

恢复后检查清单：

1. 重启 `backend-java`（或整栈）
2. 确认 Flyway `flyway_schema_history` 与当前镜像迁移版本一致
3. `GET /api/devices`、`GET /api/inspection-tasks` 有数据
4. 抽查一条证据的 `object_key` 在 MinIO 仍可读（预签名下载）
5. 登录 Keycloak 业务账号

**禁止**：用更旧的应用镜像硬回滚到含不可逆 Flyway 的库；先评估迁移可逆性或整库点恢复。

### 2.4 遥测表注意

`device_telemetry_point`（V19）随业务库备份。任务 RUNNING 期间写入频率约 0.5～1 Hz/机，
体积会随演示时长增长。运维侧可按任务归档后清理过期点：

```bash
./scripts/telemetry-prune.sh              # dry-run，默认保留 90 天
TELEMETRY_RETAIN_DAYS=60 ./scripts/telemetry-prune.sh --apply
```

---

## 3. MinIO：证据生命周期与容量

### 3.1 桶分工

| 桶 | 用途 | 删除策略 |
| --- | --- | --- |
| `skytrace-evidence` | 证据原件 / 衍生件 / 归档包 | **业务驱动**：软删 → 保留期 → `EvidenceCleanupService` 物理清 |
| `skytrace-backups` | MySQL dump | **时间驱动**：`BACKUP_RETAIN_DAYS` |
| `admin-avatars` | 管理台头像 | 低频；可与证据桶同策略或更短 |

证据侧正式策略见 [evidence-center/retention-policy.md](./evidence-center/retention-policy.md)
与 [evidence-maintenance-runbook.md](./evidence-center/evidence-maintenance-runbook.md)。
**不要**对证据桶套「N 天自动删全部对象」的 ILM，除非与业务保留期严格对齐，否则会
出现 DB 有元数据、对象已空的双真相问题。

### 3.2 容量与监控

本地/预发快速查看：

```bash
# 需要本机已安装 mc（MinIO Client）
mc alias set skytrace-minio "http://127.0.0.1:${MINIO_API_PORT:-9011}" \
  "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"

mc du skytrace-minio/skytrace-evidence
mc du skytrace-minio/skytrace-backups
mc admin info skytrace-minio
```

建议告警阈值（Grafana/磁盘均可）：

| 信号 | 阈值建议 |
| --- | --- |
| 证据桶占用 | 磁盘 70% 预警、85% 紧急 |
| 备份桶占用 | 超过「日备份大小 × 保留天数 × 1.5」 |
| MinIO 节点磁盘 | 与宿主机磁盘告警对齐 |

### 3.3 版本控制与复制（生产）

```bash
# 证据桶开启版本控制（防误删覆盖）
mc version enable skytrace-minio/skytrace-evidence

# 可选：跨站点复制（需第二套 MinIO）
# mc replicate add skytrace-minio/skytrace-evidence --remote-bucket ...
```

预发可用脚本一键套用「备份桶过期 + 证据桶版本控制」：

```bash
./scripts/minio-lifecycle-apply.sh
```

（幂等；仅改生命周期/版本配置，不删现有对象。）

### 3.4 容量治理动作顺序

1. 先跑证据维护：`hash-backfill` → `archive` → `cleanup-preview` → 小批量 `cleanup`
2. 确认业务保留期后再缩短 `BACKUP_RETAIN_DAYS` 或清理备份前缀
3. 仍不足再扩容磁盘 / 换节点

---

## 4. Qdrant：知识库重建流程

Qdrant **只存手册向量**，不存巡检任务。丢了可以重建；换嵌入模型维度时**必须**换
collection，不要混写。

### 4.1 何时重建

- 更换 `AI_OLLAMA_EMBEDDING_MODEL` 且向量维度变化
- collection 损坏 / 误删 / 与 MySQL 知识文档元数据严重不一致
- 预发想从空库验证入库链路

### 4.2 推荐步骤

脚本：[`scripts/qdrant-rebuild.sh`](../scripts/qdrant-rebuild.sh)

```bash
# 1) 删除当前 collection（默认 AI_QDRANT_COLLECTION=skytrace_knowledge）
./scripts/qdrant-rebuild.sh wipe

# 2) 确认嵌入模型已就绪
ollama pull nomic-embed-text
curl -s "http://127.0.0.1:${AI_SERVICE_PORT:-8000}/health"

# 3) 重启 AI（可选，确保连上新空 collection）
./scripts/skytrace.sh restart backend-ai

# 4) 在 /knowledge 用 ADMIN 重新上传文档（或调用入库 API）
# 5) 语义检索冒烟：同一问题应重新命中片段
```

换维度模型时：

1. 改 `.env`：`AI_OLLAMA_EMBEDDING_MODEL` + **新的** `AI_QDRANT_COLLECTION`（例如
   `skytrace_knowledge_v2`）
2. `rebuild backend-ai`
3. 全量重新上传文档
4. 确认检索正常后，再 `wipe` 旧 collection 名称

### 4.3 不要做的事

- 不同维度模型写入同一 collection
- 把巡检任务/遥测写进 Qdrant
- 仅删 MySQL 文档元数据却期望向量自动消失（需走产品删除 API，由 AI 服务同步删向量）

更多背景见 [knowledge-base.md](./knowledge-base.md)。

---

## 5. 演练清单（每季度）

- [ ] `mysql-backup.sh` 成功且 MinIO 可见新对象
- [ ] `restore-backup.sh` 在预发恢复到临时库或演练环境
- [ ] 恢复后登录 + 任务列表 + 抽查证据预签名
- [ ] `mc du` 证据桶与磁盘告警阈值仍合理
- [ ] （可选）`qdrant-rebuild.sh wipe` + 重传 1～2 份手册并检索通过
- [ ] 记录演练日期、耗时、问题到值班日志

## 6. 相关链接

- [ops.md](./ops.md) — 备份入口与值班
- [evidence-center/retention-policy.md](./evidence-center/retention-policy.md)
- [evidence-center/evidence-maintenance-runbook.md](./evidence-center/evidence-maintenance-runbook.md)
- [knowledge-base.md](./knowledge-base.md)
- [mqtt-device-sim-guide.md](./mqtt-device-sim-guide.md) — 遥测落库与回放
