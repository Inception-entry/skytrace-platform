# 07. 测试与工程质量

实施状态：**审计建议尚未实施；审计后只为现有测试补充中文说明，没有增删测试场景、断言或 fixture 行为。**

## 1. 当前测试事实

| 模块 | 实际结果 | 能证明什么 | 不能证明什么 |
| --- | --- | --- | --- |
| Backend Java | 110 tests 通过 | 当前 H2/local、service 和 security integration 覆盖行为通过 | 真 MySQL 空库 Flyway、并发、Rabbit 重投、MinIO/Temporal 故障不在结论内 |
| Gateway Java | 11 tests 通过 | 现有路由/安全断言通过 | 生产 overlay、真实 Redis 限流、客户端取消和新配置键升级未覆盖 |
| Backend AI | 17 tests 通过 | chat/vision/knowledge 的当前小范围单元行为通过 | 恶意 PDF/图片、FFmpeg timeout、Rabbit partial publish、RAG 注入、负载未覆盖 |
| Backend Node | 13 tests 通过 | DTO 布尔部分、JWT/JWKS基础、Redis adapter 契约通过 | `includeDeleted`、未知 kid 顺序放大、上传压力、断线重连、时间跨服务未覆盖 |
| Admin Service | 24 tests 通过 | auth/users 的当前 happy/部分 negative path 通过 | 日志秘密、RBAC提权、并发 super、同秒 refresh、真实 PostgreSQL 未覆盖 |
| Vue frontend | 4 个测试文件通过 | 若干关键源码/API path 形状存在 | 多为源文本正则断言，不能证明浏览器运行时、生命周期、竞态和可访问性 |
| Admin frontend | 无 test script | 无 | auth refresh/logout、RBAC、错误 UI 等核心状态机没有自动化证据 |
| Browser E2E | 发现 6 条，未在本次运行 | 用例文件可加载 | 本次没有启动全栈，不能报告 E2E passed |

## 2. 现有工程基础的正向项

- Node/前端使用 lockfile，AI 使用 `uv.lock`，Java 用 Maven dependency management。
- CI 已有两个 Java verify、Node/Admin lint/test、前端 build、Docker full-stack、三角色授权、Temporal/MySQL、告警证据链和 Playwright。
- CI 会为集成栈生成随机密码并 mask，而不是直接复用 `.env.example` 弱值。
- Gateway 和 Backend 已有安全 integration tests；Keycloak public client 采用 authorization code + PKCE，关闭 direct grants。
- Compose 大部分 host 端口绑定 loopback，降低开发栈意外公网暴露。
- 生产依赖和镜像已经有 audit/Trivy 概念，只是覆盖与供应链固定还不完整。

这些基础值得保留；整改应补齐盲点，而不是重写所有测试体系。

## 3. 最关键的测试缺口

### TQ-01：缺跨服务 contract tests

目前同一字段在 Python、Node、Java 各自定义，已经产生 `eventTime` 漂移。建议建立版本化 JSON Schema/OpenAPI contract：

```json
{
  "$id": "skytrace.detection.alarm.v2",
  "type": "object",
  "required": ["schemaVersion", "detectionId", "deviceCode", "eventTime"],
  "properties": {
    "schemaVersion": { "const": 2 },
    "detectionId": { "type": "string", "format": "uuid" },
    "eventTime": { "type": "string", "format": "date-time" }
  },
  "additionalProperties": false
}
```

Producer fixture 必须被 Java consumer 真实反序列化；Java response fixture 必须被 Node/前端 parser 验证。至少覆盖：时间、布尔 query、enum、分页、错误 envelope 和 SSE event。

### TQ-02：缺空库 migration smoke

local H2 `ddl-auto:update` 会掩盖 Flyway 缺表。新增 CI job：

1. 启动全新 MySQL Testcontainer。
2. 只运行 classpath Flyway migrations。
3. 用 docker/production profile 和 `ddl-auto=validate` 启动 context。
4. 对所有实体表、索引和约束做 schema diff。
5. 再从上一正式版本 Schema 升级到 HEAD，验证 upgrade path。

### TQ-03：缺安全负向测试

必须形成一组持续门禁：

- 操作日志：嵌套/大小写/循环秘密均脱敏，历史清理脚本 dry-run 可核对。
- RBAC：非 super 不能自提权、修改/删除/降级 super；inactive role 不可分配。
- JWT/JWKS：未知 kid flood、坏 JWK、issuer/audience/type/algorithm、弱 secret fail-fast。
- 文件：伪 MIME、HTML/polyglot、CRLF/path traversal、ZIP entry、超像素、恶意 PDF、FFmpeg 卡死。
- URL/path：`..`、编码 slash、跨源 Bearer、javascript/data/file scheme、开放跳转。
- CORS/CSRF：允许 origin、拒绝 origin、preflight、credential 模式。

### TQ-04：缺并发/幂等测试

- 同一 detection ID 两次投递只产生一条 alarm/实时事件。
- DB commit 后 ACK 前崩溃，重投仍幂等。
- 10 个并发 401 只 refresh 一次；失败全部及时 reject。
- 同一个 refresh token 并发使用只允许一个成功。
- 两个 super 并发禁用/删除，至少保留一个。
- 同用户名/角色/菜单并发创建，后者稳定 409。
- 同 scope 归档只能有一个 active job。
- 轮询/Socket connect 并发只有一条链。

这类测试应使用 barrier/latch，而不是依赖 sleep 猜测时序。

### TQ-05：缺资源和故障测试

建议在独立 nightly/预发运行，不必全部塞入每次 PR：

- 20 个接近上限图片/视频/PDF并发，记录 RSS、heap、event-loop lag、GC 和拒绝率。
- 10 万 telemetry point 查询、分页和下采样。
- 1 万 Redis 杂项 key 时在线设备查询延迟，确认不执行 KEYS。
- Rabbit/Redis/Keycloak/Temporal/Qdrant/Ollama 在启动前不可用，恢复后服务自动健康。
- 发布第 N 个服务失败，整次部署回滚。
- 磁盘接近满时归档拒绝新任务，不把节点写爆。

### TQ-06：Admin Web 无行为测试

优先引入 Vitest + React Testing Library + MSW：

1. 无 token 401 不挂起。
2. 并发 refresh success/failure。
3. logout 捕获 token，服务端撤销失败仍本地退出并提示。
4. login 成功、`/me` 失败时回滚。
5. request latest-wins 和 unmount abort。
6. 权限路由、菜单 path 和错误边界。

Vue 使用 Vitest + Vue Testing Library/MSW 覆盖 Cesium destroy、轮询取消、SSE abort、Socket singleton。当前源文本测试可保留作轻量 contract，但不应替代运行时测试。

### TQ-07：无可访问性与性能预算

- Playwright + axe：登录、菜单、表格、地图 toolbar、聊天、证据 Drawer。
- 键盘人工测试：Tab 顺序、focus trap、Escape、焦点归还、读屏 live region。
- bundle budget：按 route 统计 initial JS，而不是只看总 dist；Admin 当前单 chunk 约 1.39 MB。
- Web Vitals/真实低端设备：地图、首屏、长列表、聊天流。

## 4. 建议 CI 分层

### 4.1 每个 PR（快速、确定性）

```text
format/lint/typecheck
unit/component tests
contract tests
Java empty-MySQL migration smoke
npm production advisory + dev high advisory
pip/Java dependency advisory
Compose all-overlay config matrix
secret/IaC/static scan
```

目标是快速失败、没有外部模型依赖、没有不受控公网下载。工具版本和 Actions 固定。

### 4.2 合入 main

```text
构建全部镜像
生成 SBOM/provenance
镜像 HIGH/CRITICAL scan
完整 Docker integration
三角色授权 + Admin 权限负向用例
业务 Playwright + Admin Playwright + axe
真实 Rabbit/Temporal/MinIO/Qdrant failure/recovery 小集合
```

### 4.3 Nightly/预发

```text
真实 YOLO/GPU（若生产启用）
恶意文件 corpus
并发、性能、内存和磁盘压力
MQTT/Socket 多实例
备份恢复演练
发布/整栈回滚演练
依赖与容器全量扫描
```

### 4.4 Release candidate

- 在确定的 release SHA/OCI digest 上重跑，不复用普通 main job 的旧结果。
- 所有 P0/P1 关联用例通过。
- advisory 无未审批 release blocker。
- fresh staging 数据卷完成 OIDC、migration、告警、证据、Admin、WebSocket、SSE 冒烟。
- 归档测试结果、SBOM、镜像 digest、migration 清单和回滚目标。

## 5. 覆盖率策略

本次没有生成可靠 coverage 数据，因此不能声称当前覆盖率百分比。建议：

1. 先采集基线，不因历史低覆盖一次性阻断所有工作。
2. 对新增/修改行设置较高 diff coverage，例如 80%。
3. 对高风险模块设分支覆盖门禁：RBAC、auth refresh、时间转换、event idempotency、outbox、上传 sanitizer。
4. 不以单一覆盖率替代 mutation/negative tests；`if (!refreshToken) return` 这类死锁即使行覆盖高也可能遗漏分支后置条件。

## 6. 测试数据与时间

- 全部时间测试使用注入 `Clock`/fake timers，不调用真实 `now()` 猜结果。
- 明确 test instant、database zone 和 display zone；至少包括 UTC 跨日。
- 用户、token、事件和对象 key 使用随机/命名空间化 ID，避免并发 CI 冲突。
- 安全 corpus 只提交最小、无害复现；大文件在测试时生成，不把数百 MB fixture 放 Git。
- E2E 结束后清理本 job 的资源；不要用固定 `container_name` 造成跨 job 污染。

## 7. 可观测性验收

修复不能只断言 HTTP status；还要检查：

- 每个错误有稳定 `errorCode` 和 requestId。
- 日志不含 token/password/body secret。
- dependency/downstream、retry、DLQ、outbox lag、JWKS refresh、upload reject、FFmpeg timeout 都有 metric。
- liveness 与 readiness 分离。
- 客户端取消记录为 CANCEL，而不是伪造 500/成功。
- 告警、审计、证据时间可以从 producer 到 DB/UI 追踪同一 instant。
