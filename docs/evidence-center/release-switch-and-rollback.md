# Evidence Center 上线开关与回滚方案

> 适用范围：Evidence Center Phase 3 发布日。
>
> 目标：明确上线当天哪些开关先关、哪些后开、什么时候开、出了问题怎么回退。
>
> 文档更新时间：2026-08-11

## 1. 当前建议结论

上线当天不要把所有能力一次性放开。

推荐策略：

1. 先发布代码和数据库迁移。
2. 保持维护能力默认保守值。
3. 先只开放查询、归档、下载主链路。
4. 哈希回填先手动小批量。
5. 清理先长期 `dry-run`。
6. 正式物理清理放到更后面的灰度窗口。

一句话概括：

**先让归档稳定上线，再逐步启用维护任务，不要同一天同时打开“回填 + 正式清理”。**

## 2. 上线当天默认开关

第一次生产发布，建议使用下面这组默认值：

| 开关 | 建议值 | 说明 |
| --- | --- | --- |
| `EVIDENCE_HASH_BACKFILL_ENABLED` | `false` | 不自动扫历史对象 |
| `EVIDENCE_HASH_BACKFILL_BATCH_SIZE` | `25` | 保持默认即可 |
| `EVIDENCE_CLEANUP_ENABLED` | `false` | 不自动执行清理 |
| `EVIDENCE_CLEANUP_DRY_RUN` | `true` | 即使后续开启，也先演练 |
| `EVIDENCE_CLEANUP_RETENTION_DAYS` | `90` | 保持当前策略 |
| `EVIDENCE_CLEANUP_BATCH_SIZE` | `20` | 只是上限，不代表首次就用满 |

## 3. 推荐发布窗口

### 第 1 个窗口：代码发布窗口

目标：

- 部署新代码
- 执行数据库迁移
- 验证主链路可用

此窗口只做：

- Flyway 收口
- Java / Node / Gateway / Temporal / MinIO 健康检查
- 前端归档和下载联调

此窗口不要做：

- 自动回填
- 自动正式清理

### 第 2 个窗口：历史回填窗口

目标：

- 小批量回填历史 `contentHash`

推荐顺序：

1. 手动 `batchSize=5`
2. 观察结果
3. 再执行 `10 / 25`
4. 确认失败记录是否可追踪

此窗口仍不建议做正式物理清理。

### 第 3 个窗口：清理演练窗口

目标：

- 开启清理调度，但只做 `dry-run`

推荐配置：

```dotenv
EVIDENCE_CLEANUP_ENABLED=true
EVIDENCE_CLEANUP_DRY_RUN=true
```

观察重点：

- 候选数量是否符合预期
- 是否有不该进入候选的证据
- 业务审批记录是否能对上

### 第 4 个窗口：正式清理灰度窗口

目标：

- 极小批量执行正式物理清理

推荐顺序：

1. 手动 `batchSize=1`
2. 验证对象删除、归档包保留、数据库状态、审计日志
3. 再扩大到 `5`
4. 稳定后才考虑 `10 / 20`

第一次正式清理不建议依赖自动调度。

## 4. 上线当天推荐操作顺序

建议按下面顺序执行：

1. 备份数据库
2. 备份证据桶
3. 发布应用
4. 验证 Flyway
5. 验证服务健康
6. 验证前端主链路
7. 验证 ZIP / manifest 下载
8. 确认当天先不打开自动回填和自动正式清理
9. 发布完成后再安排单独窗口做回填

## 5. 当前建议的上线判定

### 可以随代码一起上线的能力

- 证据归档任务创建
- 归档状态查询
- ZIP 下载
- manifest 下载
- 前端归档控制台
- 维护策略查看

### 不建议和代码上线同一时刻一起打开的能力

- 自动历史哈希回填
- 自动正式物理清理

### 可以在上线后单独灰度的能力

- 手动历史哈希回填
- 清理预览
- `dry-run` 清理调度
- 小批量正式清理

## 6. 回滚原则

回滚时优先遵守下面原则：

1. 先停写，再判断是否需要回滚代码。
2. 先关开关，再谈数据恢复。
3. 能通过关闭调度止血的，不要先碰数据库。
4. 已执行的物理清理不能靠“代码回滚”恢复，只能靠归档包或备份恢复。

## 7. 出问题时先关什么

### 如果历史回填异常

先处理：

```dotenv
EVIDENCE_HASH_BACKFILL_ENABLED=false
```

再排查：

- MinIO 对象是否缺失
- 哈希失败记录
- 批次大小是否过大

### 如果清理候选异常

先处理：

```dotenv
EVIDENCE_CLEANUP_ENABLED=false
EVIDENCE_CLEANUP_DRY_RUN=true
```

再排查：

- 候选过滤条件
- 归档完整性校验
- 业务审批口径

### 如果正式清理出现异常

先处理：

```dotenv
EVIDENCE_CLEANUP_ENABLED=false
```

再核查：

- `PURGING` / `PURGED` 状态
- `purge_error`
- 审计日志
- 归档包是否仍在

## 8. 代码回滚与数据回滚边界

### 代码回滚可以解决什么

- 接口逻辑问题
- 页面显示问题
- 任务状态更新逻辑问题
- 调度误触发问题

### 代码回滚不能直接解决什么

- 已经执行的物理删除
- 已经改写的 Flyway 元数据
- 已经完成的历史哈希写回

### 这些问题怎么恢复

- 已物理删除的对象：从归档 ZIP 或备份恢复
- Flyway 元数据问题：走 DBA / 运维批准流程
- 错误回填结果：按证据编号重新校验和修复

## 9. 最小回滚动作清单

一旦发布后出现异常，建议优先做这 6 件事：

1. 关闭 `EVIDENCE_HASH_BACKFILL_ENABLED`
2. 关闭 `EVIDENCE_CLEANUP_ENABLED`
3. 保留 `EVIDENCE_CLEANUP_DRY_RUN=true`
4. 记录异常时间点
5. 记录受影响 `jobCode` / `evidenceCode`
6. 导出日志、审计、候选结果

## 10. 发布日留档要求

发布当天至少要留下这些记录：

- 发布时间
- 发布人
- 镜像或提交版本
- Flyway 验证结果
- 开关最终值
- 联调通过截图
- 压测结论
- 若有异常，记录止血动作和时间

## 11. 当前建议批准版本

如果你现在要拿给领导或评审会确认，我建议直接采用下面这版：

- 发布当天只上线主链路，不自动跑历史回填
- 发布当天不自动执行正式物理清理
- 回填使用单独窗口手动灰度
- 清理先 `dry-run`
- 正式清理放到更后窗口，且从 `batchSize=1` 开始
- 回滚优先关调度和维护开关，不先碰数据库

## 12. 与其他文档的关系

- 总体闭环顺序见 [go-live-checklist.md](./go-live-checklist.md)
- 清理审批规则见 [retention-policy.md](./retention-policy.md)
- 联调与压测见 [integration-acceptance-checklist.md](./integration-acceptance-checklist.md)
- 维护与压测细节见 [evidence-maintenance-runbook.md](./evidence-maintenance-runbook.md)
