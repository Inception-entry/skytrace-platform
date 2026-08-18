# Evidence Center 真实环境联调与验收清单

> 适用范围：`phase 3` 上线前联调、验收、压测。
>
> 目标：确认 Evidence Center 不只是“代码实现完成”，而是“前端、BFF、Java、MinIO、Temporal、权限、下载链路都能在真实环境里闭环工作”。
>
> 文档更新时间：2026-08-11

## 1. 验收结论怎么判定

只有同时满足下面 4 条，才能认为第四步真正完成：

1. 前端页面可以完整发起归档并看到状态变化。
2. BFF、Java、MinIO、Temporal 在真实链路中都实际参与并返回预期结果。
3. 失败、重试、权限、下载这些非 happy path 场景也被验证过。
4. 有可留档的验收证据，而不是只有口头“测过了”。

## 2. 联调前准备

联调前必须先确认以下前置条件：

- [ ] Flyway 已收口，服务能在目标环境正常启动
- [ ] 历史 `contentHash` 回填方案已确认
- [ ] 归档后清理策略已评审
- [ ] Gateway、Node、Java、Temporal、MinIO、Keycloak 均已部署
- [ ] 有可登录的 `ADMIN`、`OPERATOR`、`VIEWER` 账号
- [ ] 已准备联调数据

建议准备两类数据：

### 小批量业务数据

用于功能联调：

- 1 个存在证据的 `TASK`
- 1 个存在证据的 `ALARM`
- 图片、视频各至少 1 条

### 压测数据

用于性能和恢复验证：

- 单任务 20 / 50 / 100 条证据
- 单文件 8 MiB / 16 MiB
- 至少 1 轮 MinIO 短时故障注入演练

## 3. 环境健康检查

先确认环境是“可测”的，再进入业务联调。

### 服务级检查

- [ ] Gateway 健康
- [ ] backend-node 健康
- [ ] backend-java 健康
- [ ] Temporal 可访问
- [ ] MinIO 可访问
- [ ] Keycloak 可登录

### 配置级检查

- [ ] `MINIO_PUBLIC_ENDPOINT` 指向真实前端可访问域名
- [ ] `MINIO_PRESIGN_DOWNLOAD_TTL_SECONDS` 符合预期
- [ ] `TEMPORAL_TASK_QUEUE` 与 Worker 注册一致
- [ ] `EVIDENCE_HASH_BACKFILL_ENABLED` / `EVIDENCE_CLEANUP_ENABLED` / `EVIDENCE_CLEANUP_DRY_RUN` 已按当前阶段设置

## 4. 角色与权限验收

这一步必须先做，不然后面看到的很多报错都无法判断是不是权限问题。

### ADMIN

应允许：

- 查看证据列表与详情
- 上传证据
- 删除与恢复证据
- 创建归档任务
- 查询归档任务
- 下载 ZIP 与 manifest
- 查看维护策略
- 触发哈希回填
- 执行清理预览
- 执行正式清理

### OPERATOR

应允许：

- 查看证据列表与详情
- 上传证据
- 删除与恢复证据
- 创建归档任务
- 查询归档任务
- 下载 ZIP 与 manifest

应拒绝：

- 访问 `/api/admin/evidence-maintenance/**`

### VIEWER

应允许：

- 查看证据列表与详情

应拒绝：

- 上传
- 删除
- 恢复
- 创建归档任务
- 触发维护接口

### 验收项

- [ ] `ADMIN` 行为全通过
- [ ] `OPERATOR` 被正确限制在维护接口之外
- [ ] `VIEWER` 无法执行写操作
- [ ] 401 / 403 行为符合预期

## 5. 前端页面联调

前端重点在 [EvidenceView.vue](../../frontend/src/views/EvidenceView.vue) 的归档控制台和证据列表行为。

### 页面级检查

- [ ] 页面可以正常打开
- [ ] 证据列表能正常加载
- [ ] 证据详情抽屉能正常打开
- [ ] 归档控制台能显示 `TASK / ALARM` 归档入口
- [ ] 归档状态标签显示正确

### 交互级检查

- [ ] 输入 `TASK` 范围值可以创建归档任务
- [ ] 输入 `ALARM` 范围值可以创建归档任务
- [ ] 前端能自动轮询 `PENDING / RUNNING`
- [ ] 任务完成后显示 `packageContentHash`
- [ ] 任务失败后显示 `errorMessage`
- [ ] 已完成任务可下载 ZIP
- [ ] 已完成任务可下载 manifest

### 状态显示检查

- [ ] `ACTIVE` 显示正确
- [ ] `ARCHIVED` 显示正确
- [ ] `PURGING` 显示正确
- [ ] `PURGED` 显示正确
- [ ] `PURGING / PURGED` 证据不再显示恢复按钮

## 6. BFF 与 Java 接口联调

需要确认 Node BFF 和 Java 之间不是“表面通”，而是参数、错误、状态都一致。

### 关键接口

- `POST /api/evidence/archive-jobs`
- `GET /api/evidence/archive-jobs/{jobCode}`
- `POST /api/evidence/archive-jobs/{jobCode}/download-url`
- `POST /api/evidence/archive-jobs/{jobCode}/manifest-url`
- `POST /api/evidence/{evidenceCode}/download-url`

### 联调检查

- [ ] BFF 能正确透传 `scopeType/scopeValue`
- [ ] Java 能正确校验 `TASK` / `ALARM`
- [ ] 错误信息不会在 BFF 层被吞掉
- [ ] 任务状态在前端、BFF、Java 三层一致
- [ ] 下载接口返回的是可用 presigned URL，而不是内部地址

## 7. 归档主链路验收

这一步是第四步的核心。

### 场景 A：任务归档

1. 选择一个真实 `TASK`
2. 在前端创建归档任务
3. 观察状态从 `PENDING -> RUNNING -> COMPLETED`
4. 下载 ZIP
5. 下载 manifest

验收项：

- [ ] 归档任务创建成功
- [ ] Temporal Workflow 被真正触发
- [ ] 归档文件数与预期一致
- [ ] ZIP 可下载
- [ ] manifest 可下载
- [ ] `packageContentHash` 可见

### 场景 B：告警归档

1. 选择一个真实 `ALARM`
2. 创建归档任务
3. 重复上面的状态和下载检查

验收项：

- [ ] 告警归档范围生效
- [ ] 告警链路与任务链路都能工作

## 8. 下载链路验收

下载链路必须单独验收，因为很多问题不是归档本身，而是 presigned URL 可用性。

### ZIP / manifest 下载

- [ ] 返回 URL 可直接下载
- [ ] URL 指向正确域名
- [ ] URL 不暴露内部容器地址
- [ ] URL 在有效期内可用
- [ ] URL 过期后会失效

### 单证据下载

- [ ] 单证据下载接口可用
- [ ] 已软删除证据的访问行为符合策略
- [ ] `PURGED` 证据不可再下载原件

## 9. Temporal 与失败恢复验收

这部分不能只看成功场景。

### 正常执行

- [ ] Workflow 真正启动
- [ ] Activity 真正执行
- [ ] 任务最终完成

### 失败恢复

建议至少做一轮 MinIO 短时故障演练。

验收项：

- [ ] MinIO 暂时不可用时任务不会直接假成功
- [ ] Temporal 出现重试
- [ ] MinIO 恢复后任务可以继续完成
- [ ] 前端能看到最终完成或失败状态

## 10. MinIO 与归档产物验收

### 产物存在性

- [ ] `archives/<jobCode>/<jobCode>.zip` 存在
- [ ] `archives/<jobCode>/manifest.json` 存在

### 内容一致性

- [ ] ZIP 哈希与 `packageContentHash` 一致
- [ ] 独立 manifest 与 ZIP 内嵌 manifest 一致
- [ ] manifest 中文件数量与数据库统计一致

## 11. 维护接口联调

这一步不是正式清理，而是确认维护入口在真实环境中可用。

### 策略接口

- [ ] `GET /api/admin/evidence-maintenance/policy` 返回当前策略

### 哈希回填

- [ ] 小批量回填可以执行
- [ ] 成功、失败数量返回正确
- [ ] 失败记录可追踪

### 清理预览

- [ ] `cleanup-preview` 能返回候选
- [ ] `dryRun=true` 不会真正删除对象

### 正式清理

首次上线前建议只演练，不一定马上在真实环境执行。

如果执行正式清理，必须额外确认：

- [ ] `confirmation=PURGE_ARCHIVED_EVIDENCE`
- [ ] 先 `batchSize=1`
- [ ] 删除后审计记录完整

## 12. 压测验收

推荐直接复用：
[verify-evidence-archive.sh](../../scripts/ci/verify-evidence-archive.sh)

### 常规压测

- [ ] 20 条证据压测通过
- [ ] 50 条证据压测通过
- [ ] 大文件压测通过

### 故障恢复压测

- [ ] MinIO 短时中断后任务恢复成功
- [ ] Temporal `attempt > 1`

### 关注指标

- [ ] 单任务耗时
- [ ] ZIP 生成耗时
- [ ] 失败率
- [ ] 重试次数
- [ ] 下载成功率

## 13. 验收留档要求

每轮联调至少保留下列证据：

- 环境版本与时间
- 测试账号角色
- 归档 `jobCode`
- 前端页面截图
- Temporal 执行截图或记录
- MinIO 归档对象截图或记录
- ZIP / manifest 下载结果
- 压测参数
- 失败样本与结论

## 14. 推荐验收顺序

建议按下面顺序执行，不要乱跳：

1. 环境健康检查
2. 角色与权限验收
3. 前端页面联调
4. BFF 与 Java 接口联调
5. 任务归档与告警归档主链路
6. 下载链路
7. Temporal 与失败恢复
8. 维护接口
9. 压测

## 15. 完成定义

第四步完成，至少要满足：

- [ ] 前端真实联调完成
- [ ] BFF/Java/MinIO/Temporal 真链路跑通
- [ ] 权限模型验证完成
- [ ] 下载链路验证完成
- [ ] 失败恢复验证完成
- [ ] 压测完成
- [ ] 验收留档完整

按本节清单现场打勾即可；上线节奏见 [go-live-checklist.md](./go-live-checklist.md)。
