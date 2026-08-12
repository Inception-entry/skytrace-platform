# Evidence Center 归档后清理策略

> 适用范围：Evidence Center Phase 3 已归档证据。
>
> 文档目标：把“代码已经具备物理清理能力”转换成“业务、研发、运维都能执行的正式规则”。
>
> 文档更新时间：2026-08-11

## 1. 结论先行

当前系统已经支持证据归档后的物理清理，但生产环境默认仍应保持保守策略：

- 默认不开启自动物理清理
- 默认开启时也先只做 `dry-run`
- 默认保留期为 `90` 天
- 默认只允许 `ADMIN` 手动触发正式清理

这份策略的核心原则只有一句话：

**先保证归档完整、可校验、可追溯，再谈物理删除。**

## 2. 哪些证据允许物理删除

不是所有“已归档”证据都能删。只有同时满足下面条件，才允许进入物理清理候选：

1. 证据已经被业务侧软删除，`deleted=true`。
2. 证据归档状态为 `ARCHIVED`，不能是 `ACTIVE`、`PURGING` 或 `PURGED`。
3. `content_hash` 非空，系统仍能证明原件内容摘要。
4. `archive_batch_code` 非空，可以追溯到归档任务。
5. `archived_at` 和 `deleted_at` 都早于当前保留期截止时间。
6. 对应归档任务状态为 `COMPLETED`。
7. 归档 ZIP 和独立 `manifest.json` 在 MinIO 中都存在。
8. ZIP 的 `package_content_hash` 与本轮重算结果一致。
9. ZIP 内嵌 `manifest.json` 与独立 `manifest.json` 字节完全一致。
10. 当前证据的 `evidenceCode`、`contentHash`、`sizeBytes`、`archivePath` 与 manifest 中唯一条目完全匹配。

任一条件不满足，都必须阻断物理删除。

## 3. 哪些证据不允许物理删除

下面这些场景一律禁止删除原始对象：

- 证据仍在 `ACTIVE`
- 证据尚未软删除
- 证据未完成归档
- 证据没有 `content_hash`
- 归档任务失败或未完成
- ZIP、manifest 任一缺失
- ZIP 哈希或 manifest 校验不一致
- 旧版本归档任务没有 `package_content_hash`
- 审计写入失败

简单说：

**只要系统无法继续证明“删掉原件后仍然可追溯”，就不能删。**

## 4. 真正删除什么，不删除什么

### 会删除的对象

- 原始对象 `object_key`
- 图片缩略图 `thumbnail_object_key`
- 视频封面 `poster_object_key`

### 不会删除的对象

- `archives/<jobCode>/<jobCode>.zip`
- `archives/<jobCode>/manifest.json`
- MySQL 中的证据记录
- `evidence_code`
- `content_hash`
- `archive_batch_code`
- 审计日志

删除成功后，证据进入 `PURGED`，数据库保留墓碑记录用于追溯和审计。

## 5. 保留期规则

当前默认保留期为 `90` 天。

含义不是“上传后 90 天”，而是必须同时满足：

- `archived_at` 早于保留期截止时间
- `deleted_at` 早于保留期截止时间

也就是说：

**先归档，再软删除，再等待保留期同时成熟，才允许进入清理候选。**

如果后续业务要调整保留期，建议遵守下面原则：

- 小于 `30` 天：不建议，除非有明确合规豁免
- `90` 天：当前推荐默认值
- 大于 `90` 天：更稳妥，但会增加存储成本

## 6. 执行职责

### 业务负责人

负责确认：

- 哪类证据允许删除
- 保留期是否仍为 `90` 天
- 是否需要按业务线区分策略

### 研发负责人

负责确认：

- 技术条件与代码逻辑一致
- 清理只删除原件，不误删归档包
- 审计链路完整
- 故障恢复路径可用

### 运维 / DBA

负责确认：

- 执行前完成数据库和对象存储备份
- 执行窗口受控
- 出现异常时可暂停任务

### 审计复核人

负责确认：

- 删除前有候选清单
- 删除后有成功/失败审计
- 能追溯到归档批次和证据编号

## 7. 谁可以执行

正式物理清理只允许 Keycloak `ADMIN` 角色触发。

`OPERATOR` 和 `VIEWER` 不允许：

- 触发正式清理
- 跳过 `dry-run`
- 绕过确认串

正式清理必须同时满足：

```text
dryRun=false
confirmation=PURGE_ARCHIVED_EVIDENCE
```

如果缺少确认串、确认串错误，或者 `dryRun` 不是严格布尔值，请求必须失败。

## 8. 推荐执行流程

生产环境建议严格按下面顺序执行：

1. 先完成历史 `contentHash` 回填。
2. 再保持一段时间 `cleanup-preview` 或 `dry-run=true`。
3. 人工复核候选证据编号、归档批次、软删除时间、归档时间。
4. 先用 `batchSize=1` 做正式清理。
5. 验证对象删除、归档包保留、数据库状态变为 `PURGED`。
6. 再逐步扩大到 `5 -> 10 -> 20`。

第一次上线时，不建议直接开启全自动正式清理。

## 9. 灰度建议

### 第 1 阶段

- `EVIDENCE_CLEANUP_ENABLED=false`
- 仅查看策略与候选，不做任何删除

### 第 2 阶段

- `EVIDENCE_CLEANUP_ENABLED=true`
- `EVIDENCE_CLEANUP_DRY_RUN=true`
- 连续观察候选是否稳定

### 第 3 阶段

- 手动执行 `dryRun=false`
- `batchSize=1`

### 第 4 阶段

- 手动执行 `batchSize=5`
- 无异常后再提升到 `10`、`20`

### 第 5 阶段

- 是否开启自动正式清理，由业务和运维共同审批

## 10. 审计要求

正式清理必须满足下面审计要求：

- 管理接口触发行为进入通用审计
- 每条物理清理写入独立 `STARTED`
- 删除成功后写入 `SUCCESS`
- 删除失败后写入 `FAILURE`

如果 `STARTED` 审计写入失败，必须禁止删除。

## 11. 失败恢复规则

如果删除过程中失败：

- 证据状态回退到 `ARCHIVED`
- 保存 `purge_error`
- 归档包继续作为恢复来源保留

如果进程在 `PURGING` 中断：

- 超过 `cleanupStaleClaimHours` 后允许下一轮释放认领

如果对象部分删除：

- MinIO 删除按幂等处理
- 可以安全重试

## 12. 当前建议批准版本

如果你现在要推动团队评审，我建议直接按下面版本去谈：

- 允许删除范围：仅限已软删除且已完整归档校验通过的证据
- 默认保留期：`90` 天
- 默认执行模式：先 `dry-run`
- 正式执行角色：仅 `ADMIN`
- 首次正式灰度：`1 -> 5 -> 10 -> 20`
- 审计要求：必须保留删除前后审计
- 永不删除：归档 ZIP、manifest、数据库墓碑、哈希与归档批次信息

## 13. 与其他文档的关系

- 上线节奏与验收顺序见 [go-live-checklist.md](./go-live-checklist.md)
- 技术条件、接口与压测见 [evidence-maintenance-runbook.md](./evidence-maintenance-runbook.md)
- 归档与合规背景见 [phase-3-archive-compliance.md](./phase-3-archive-compliance.md)
