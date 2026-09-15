# JV-02 证据 API 上海 DATETIME → Instant：实施说明

适用分支：`fix/evidence-shanghai-instant`  
只修 **证据查询 / 归档响应 / 搜索范围** 把 `LocalDateTime` 当成 UTC 的转换。  
**不要**把库字段改成 `Instant`/`TIMESTAMP`，不要写死 `plusHours(8)`，不要动 pypdf、Keycloak、AUTH-004。证据编号、归档 job code 的 UTC 日期前缀是标识符，不是 API instant，本刀不动。

旧编号：JV-02。路线图 Wave 1B 第 3 条。上一刀是告警 `eventTime`（`#161`）。

---

## 1. 一句话

MySQL `DATETIME` 和实体 `LocalDateTime.now()` 存的是 **Asia/Shanghai 墙钟**。证据搜索和详情却用 `ZoneOffset.UTC` 转成 `Instant`，于是上海 `2026-08-24 16:00` 被返回成 `16:00Z`，正确值应是 `08:00Z`；`startTime`/`endTime` 同样整体错 8 小时。清理服务已经按应用时区解释同一列，两边必须对齐。

本分支约定：读写这条 DATETIME 都走已有的 `DatabaseTimes.ZONE`（`Asia/Shanghai`），不新增 `app.database` 配置，也不做 `v1.3.0` 的 UTC at rest 迁移。

---

## 2. 现在怎么坏的

```text
写入：LocalDateTime.now()           → 库里 2026-08-24 16:00（上海）
查询：toInstant(..., UTC)           → JSON "2026-08-24T16:00:00Z"
搜索：toLocal(startTime, UTC)       → Instant 08:00Z 对不上 16:00 墙钟
清理：ZonedDateTime.now()           → 按 JVM/应用时区解释同一列
```

正确对应：墙钟 `2026-08-24 16:00` Asia/Shanghai = instant `2026-08-24T08:00:00Z`。  
UTC 跨日：instant `2026-08-24T16:00:00Z` = 上海 `2026-08-25 00:00`。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 实体/库改 `Instant`/`TIMESTAMP` | 无版本切契约，应走 `v1.3.0` |
| `plusHours(8)` 或写死 `+08:00` | 必须用 `ZoneId`，便于 DST 区对照 |
| 新增 `DatabaseTimeProperties` | 复用 `DatabaseTimes.ZONE` |
| 改证据编号 / 归档 job code 的 UTC 日 | 那是 ID 前缀，不是响应 instant |
| 修 pypdf、Keycloak 开发用户、AUTH-004 | 别的 PR |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `DatabaseTimes` | 增加 `toInstant(LocalDateTime)`，按 `ZONE` 转；包内重载带 `ZoneId` 供 DST 单测 |
| `EvidenceQueryService` | 搜索 `startTime`/`endTime`、详情/摘要 `createdAt`/`reviewedAt` 改走 `DatabaseTimes` |
| `EvidenceArchiveService` | 归档任务 `createdAt`/`completedAt` 同样改走 `DatabaseTimes` |
| `EvidenceCleanupService` | cutoff 明确用 `DatabaseTimes.ZONE`，不再依赖 JVM 默认时区碰巧是上海 |
| 测试 | 上海 16:00→08:00Z、UTC 跨日、America/New_York DST、详情/归档 JSON Instant |
| 本文 + 审计 README / `02` / `10` | 挂索引 |

遗留 API `EvidenceAssetResponse.createdAt` 仍是 `LocalDateTime` 墙钟，不转 Instant。

---

## 5. 怎么确认

```bash
cd backend-java && mvn -q -Dtest=DatabaseTimesTest,EvidenceQueryServiceTest,EvidenceArchiveServiceTest,EvidenceCleanupServiceTest test
```

- 上海 `2026-08-24T16:00:00` → `2026-08-24T08:00:00Z`
- `2026-08-24T16:00:00Z` → 上海 `2026-08-25T00:00:00`
- `America/New_York` 夏季 12:00 不是 UTC+8
- 查询/归档响应 Instant 与上面一致
- 源码中证据查询/归档响应不再出现 `atZone(ZoneOffset.UTC)` / `ofInstant(..., UTC)`

---

## 6. 再下一刀

RB-07：升级 `pypdf`。RB-04 Keycloak 开发用户仍是 P0。AUTH-004 仍是 P1。
