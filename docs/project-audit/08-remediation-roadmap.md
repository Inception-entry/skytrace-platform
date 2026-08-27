# 08. 分阶段整改路线图

实施状态：**规划建议，未执行。**

## 1. 拆分原则

- 每个 PR 只解决一个清晰风险域，能独立测试和回滚。
- 先做历史数据/凭据止血，再做代码修复；只修未来写入不能消除已泄露秘密。
- 先加兼容读取，再改变写入，最后删除旧协议；时间、事件和认证都遵循这个顺序。
- additive migration 和代码分阶段上线，避免回滚时旧代码读不了新 Schema。
- 依赖大升级与业务逻辑修复分开，降低定位成本。
- 每个 P0/P1 都必须有自动化回归，不以人工点一下代替。

## 2. 依赖关系

```text
历史秘密排查/轮换 ──> 日志脱敏 ──> Admin 认证/RBAC 回归

时间语义 ADR ──> 当前协议兼容修复 ──> v2 事件双读/双写 ──> UTC 数据迁移

detectionId/唯一键 ──> consumer 幂等 ──> outbox/confirm/DLQ ──> 故障恢复演练

统一上传预算 ──> magic-byte/路径/像素限制 ──> 流式处理 ──> 并发压力门禁

immutable release manifest ──> 整栈回滚 ──> 蓝绿切换 ──> 自动化生产门禁
```

## 3. 阶段 0：立即止血与盘点

目标：不改变产品协议，先确认是否已有真实暴露。

| 工作项 | 输出 | 完成条件 |
| --- | --- | --- |
| 扫描 `sys_operation_log.params` | 只读统计和受影响账号清单 | 不在普通日志输出秘密；由安全负责人保管结果 |
| 轮换可能暴露的 Admin 凭据/token | 轮换记录 | 旧 refresh/session 全部失效 |
| 盘点生产 Keycloak 开发用户 | realm 用户/session 清单 | 开发账号禁用/删除，client secret 按需轮换 |
| 检查 seed 是否在生产运行过 | deployment/database 证据 | 默认 `admin/Admin@123` 不可登录 |
| 核对生产 eventTime 样本 | producer、Rabbit、DB、API、UI 对照 | 明确当前偏移范围和回填需求 |
| 冻结 mutable `latest` 生产发布 | 临时发布规则 | 只接受当前已构建的不可变 SHA tag |

阶段 0 可能涉及真实生产状态，必须由有权限的运维/安全人员执行；审计文档不授权自动删除用户或历史记录。

## 4. 阶段 1：`v1.2.2` 发布阻断修复

目标：保持现有外部 API 尽量兼容，关闭 P0 和最直接 P1。

### Wave 1A：Admin 安全

1. 操作日志递归脱敏；失败审计；历史清理工具先 dry-run。
2. super user/role 不变量和权限子集检查。
3. super 并发保护，真实 PostgreSQL 测试。
4. 删除公开 seed 密码，改一次性强 secret；已部署账号单独轮换。
5. refresh token 加 jti、原子消费、family/reuse 基础；JWT fail-fast 和限流。
6. 管理员改密与 session 撤销同事务。

### Wave 1B：跨服务正确性

1. 修 `includeDeleted` 严格布尔解析。
2. 形成时间 ADR；先按当前上海 DATETIME contract 修 AI/Node/Java 双向转换。
3. 修 Java Evidence API 8 小时转换错误。
4. 为 detection event 增加兼容的 optional `schemaVersion/detectionId`；consumer 唯一键去重。
5. 增加空 MySQL Flyway migration 和 test。

### Wave 1C：直接攻击面

1. 升级 `pypdf >= 6.15.0`、`h2 >= 4.4.1`，回归 lock。
2. Admin runtime dependencies 升级到无当前 advisory 的兼容组合。
3. 图片/视频/PDF bounded read，像素/page/chunk/frame/time budget。
4. FFmpeg timeout、协议/stdin、错误脱敏。
5. Java/Node/Admin 上传 magic-byte、ZIP entry 和文件名安全。
6. JWKS cooldown/negative cache。

### Wave 1D：Web 会话

1. 修 Admin refresh 永久挂起。
2. 修 logout 服务端撤销竞态和 partial login。
3. 补 Admin auth 行为测试。
4. Admin Nginx 请求体、安全头和 timeout。

阶段 1 release gate：见 `01-release-blockers.md`。通过后才生成 `v1.2.2-rc.1`。

## 5. 阶段 2：可靠性与运维加固

目标：消除“依赖短暂失败后永久坏掉”和发布混合版本。

- transactional outbox 覆盖 Rabbit/Temporal；publisher confirm、DLQ、有限 retry。
- MQTT 生命周期化无限退避；readiness 呈现连接状态。
- Rabbit/Socket/AI lifecycle 正确 shutdown/reconnect。
- Knowledge generation 切换，不先删后写。
- 发布脚本整次回滚，强制不可变 tag，输出 release manifest。
- Keycloak local/prod realm 分离；真实域名 fresh-volume OIDC test。
- staging/production 公共 URL/CORS/issuer 从一个 domain 派生并 fail-fast。
- 移除 fixed container/network names，为蓝绿和扩容准备。
- 非 root、cap drop、read-only rootfs、资源/临时盘/log 限额。

如果这些改动不增加用户功能且保持兼容，可继续放 `1.2.x`；若批量合入并改变部署能力/运维 contract，建议进入 `v1.3.0`。

## 6. 阶段 3：性能、可扩展性与前端生命周期

- Redis KEYS 改 ZSET/SCAN。
- 数据库复合索引和 EXPLAIN 基线。
- Java/AI 列表 metadata 分页、消除 N+1、遥测 cursor/downsample。
- 证据归档配额、同 scope 唯一 job 和磁盘保护。
- Cesium destroy、DPR budget、轨迹缓存。
- 所有 polling/SSE/Socket/request 支持取消和 latest-wins。
- 路由 lazy load、bundle budget。
- 统一 API error/envelope、安全 URL helper。
- Admin/Vue component tests、MSW、axe 和性能监控。

## 7. 阶段 4：正式协议演进

这是独立设计项目，不能当作一个小补丁直接替换：

### 7.1 UTC 时间

1. 定义 v2 JSON event：`OffsetDateTime/Instant` + `schemaVersion`。
2. consumer 先支持 v1/v2。
3. producer 切 v2，同时观察解析/偏移指标。
4. 新数据库列或 UTC 存储方案双写、回填、校验。
5. API/UI 统一按用户/站点时区展示。
6. 过完兼容窗口再停 v1。

### 7.2 Admin Cookie 认证

1. 服务端先支持 HttpOnly refresh cookie 和旧 body token 双协议。
2. 增加 CSRF/Origin、CORS、rotation/reuse detection。
3. 新前端切换，access token 只在内存。
4. 观测旧协议使用者，版本化下线。

### 7.3 多租户 AI/RAG

1. 定义 tenant/subject identity propagation。
2. Redis session key 和 Qdrant payload/filter 加 tenant。
3. 文档 upload/search/delete 权限。
4. 对旧未分租户数据做归属/隔离迁移。

这些若对外 contract 仍双栈兼容，可以作为 `v1.3.0`；若直接移除旧格式或客户端必须同步改造，则评估 `v2.0.0`。

## 8. 建议 issue/PR 命名与验收字段

每个 issue 至少包含：

```text
风险 ID：
当前证据 path:line：
威胁/失败场景：
兼容性影响：
Schema/API/event 影响：
建议实现：
自动化测试：
监控指标：
迁移步骤：
回滚步骤：
完成定义：
```

PR description 要明确“本 PR 没解决什么”，避免例如只升级 pypdf 后就把所有 PDF 资源治理标为完成。

## 9. 完成定义

问题只有同时满足以下条件才可从报告中关闭：

1. 代码/配置变更已 review 并合入。
2. 新增能在修复前失败、修复后通过的回归测试。
3. 生产/预发所需 migration、secret、数据清理已执行或有明确步骤。
4. 监控可识别再次发生。
5. 回滚已验证，不只写了命令。
6. 文档和版本字段同步。

