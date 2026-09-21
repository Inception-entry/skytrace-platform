# 09. 版本与发版建议

实施状态：**P0 代码已收口为 `1.2.2` 补丁范围；正式生产 tag 仍未打。**

## 1. 现在该不该发

结论：**不要直接发生产 `v1.2.2`。** 可以准备 **`v1.2.2-rc.1`**。

P0 代码（日志脱敏、seed、RBAC、`includeDeleted`、告警时间、证据 Instant、`pypdf`、生产 Keycloak 不导入开发用户）已经或即将在独立 PR 合入。这些是向后兼容的安全/正确性补丁，SemVer 用 **1.2.2**。

还不能当生产 GA 的原因：

- Keycloak 拆分 PR 需先合入；已部署库内开发账号要运维盘点，改 JSON 不会删旧用户。
- 操作日志历史数据未清理，凭据轮换未做。
- 原 `1.2.2` 大清单里的 P1 仍在：detection 幂等、整栈回滚。Flyway 空库已做，见 [jv-01-flyway-empty-schema.md](jv-01-flyway-empty-schema.md)。cgroup 内存上限见 [ai-01-pdf-cgroup-memory.md](ai-01-pdf-cgroup-memory.md)。multer 落盘见 [bn-04-multer-disk.md](bn-04-multer-disk.md)。可终止 PDF 解析见 [ai-01-pdf-killable-process.md](ai-01-pdf-killable-process.md)。上传流转发见 [bn-04-upload-stream.md](bn-04-upload-stream.md)。PDF 页数/超时见 [ai-01-pdf-parse-bounds.md](ai-01-pdf-parse-bounds.md)。Admin npm 见 [rb-16-admin-npm-advisories.md](rb-16-admin-npm-advisories.md)。`h2` 见 [ai-16-h2-upgrade.md](ai-16-h2-upgrade.md)。

本版把 `1.2.2` **收窄为 P0 热修**。未完成的 P1 记入发版说明「已知限制」，正式 tag 前要修完或书面豁免。不要把时间协议改成 UTC（那是 `1.3.0`）。

## 2. 修完后建议发什么版本

推荐目标：**`v1.2.2`**，但发布顺序是：

1. 合入 Keycloak 拆分。
2. 版本字段已对齐 `1.2.2` 后打 **`v1.2.2-rc.1`**，fresh staging。
3. P1 关闭或豁免后，再打不可移动的 **`v1.2.2`**。

不要发 `1.3.0`：没有新能力。不要发 `2.0.0`：没有破坏契约。

## 3. `v1.2.2` 建议范围

### 必须包含（P0，本版）

1. Admin 日志秘密脱敏（#118）。历史排查/清理/轮换仍是运维项。
2. Admin super/RBAC 提权修复（#158）。真实 PostgreSQL 并发套件未做。
3. 移除 seed 默认管理员密码（#157）；生产 Keycloak 导入去掉开发用户（待合入）。
4. Node `includeDeleted` 严格布尔（#160）。
5. AI/Node/Java 当前协议下的时间兼容，以及 Evidence Instant 8 小时修正（#161、#162）。
6. `pypdf >= 6.15.0`（#163，锁到 6.18.1）。`h2` 见 [ai-16-h2-upgrade.md](ai-16-h2-upgrade.md)。Admin 生产 npm 见 [rb-16-admin-npm-advisories.md](rb-16-admin-npm-advisories.md)。

### 正式 tag 前仍建议完成（可豁免后进 `1.2.3`）

1. refresh token 唯一/原子（RB-10）、JWT secret 启动 fail-fast（RB-11，见 [rb-11-jwt-secret-failfast.md](rb-11-jwt-secret-failfast.md)）、基础认证限流（AS-06，见 [as-06-auth-rate-limit.md](as-06-auth-rate-limit.md)）、Node BFF JWKS kid 冷却（RB-13，见 [bn-03-jwks-kid-cooldown.md](bn-03-jwks-kid-cooldown.md)）。
2. PDF/图片/视频的直接资源边界与 FFmpeg timeout。Admin 头像 magic-byte 见 [as-09-admin-avatar-magic.md](as-09-admin-avatar-magic.md)；AI 有界读入/FFmpeg 超时见 [ai-03-upload-ffmpeg-bounds.md](ai-03-upload-ffmpeg-bounds.md)；像素预算见 [ai-04-image-pixel-budget.md](ai-04-image-pixel-budget.md)；Java/Node 上传 magic-byte 见 [rb-12-java-node-upload-magic.md](rb-12-java-node-upload-magic.md)；PDF 页数/超时见 [ai-01-pdf-parse-bounds.md](ai-01-pdf-parse-bounds.md)；可终止 PDF 解析见 [ai-01-pdf-killable-process.md](ai-01-pdf-killable-process.md)；上传流转发见 [bn-04-upload-stream.md](bn-04-upload-stream.md)；multer 落盘见 [bn-04-multer-disk.md](bn-04-multer-disk.md)；PDF 解析 cgroup 内存上限见 [ai-01-pdf-cgroup-memory.md](ai-01-pdf-cgroup-memory.md)。
3. Admin 前端 refresh deadlock（RB-08）、logout 撤销（RB-09）、partial-login（FE-05，见 [fe-05-admin-partial-login.md](fe-05-admin-partial-login.md)）。
4. Flyway 空库见 [jv-01-flyway-empty-schema.md](jv-01-flyway-empty-schema.md)。
5. detection ID/consumer 幂等的兼容第一阶段。
6. 生产不可变 image tag、整次部署回滚和真实域名 OIDC 预检。OIDC URL 派生见 [rb-15-oidc-public-domain.md](rb-15-oidc-public-domain.md)。
7. Admin 开发链 vitest mocker，或书面豁免。生产 npm 见 [rb-16-admin-npm-advisories.md](rb-16-admin-npm-advisories.md)。AI `h2` 见 [ai-16-h2-upgrade.md](ai-16-h2-upgrade.md)。

### 建议包含但可拆到 `v1.2.3`/`v1.3.0`

- 完整 transactional outbox。
- 全部非 root/read-only/cap drop。
- Redis KEYS、N+1、全部分页和性能优化。
- 前端全面 lazy load、统一 API helper、全部可访问性修复。
- 多租户 RAG 和正式 UTC at-rest 迁移。
- 蓝绿部署、完整 SBOM 签名和生产拓扑重构。

任何延期 P1 都要有书面豁免、补偿控制、责任人和截止日期；P0 不建议延期。

## 4. 哪些改动会改变版本建议

| 变更 | 建议版本 |
| --- | --- |
| 保持 API/事件兼容的安全与 bug fix | `1.2.2` |
| 新增 optional 字段，consumer 先兼容，旧 client 仍工作 | 可在 `1.2.2`；若形成新能力，倾向 `1.3.0` |
| 新增 async workflow 查询、Admin 公网域名、正式部署能力等非破坏功能 | `1.3.0` |
| eventTime 同字段直接从无 offset 本地时间改成 UTC offset，旧 consumer 会失败 | 不允许无版本发布；做双栈后 `1.3.0`，否则 `2.0.0` |
| Admin refresh 从 JSON body 直接移除并强制 Cookie，外部 client 需同步改 | 双协议迁移可 `1.3.0`；硬切为 breaking 时评估 `2.0.0` |
| 删除/重命名 REST、Rabbit、Socket/MQTT 字段或破坏性 Schema | `2.0.0` 或先提供兼容层 |

不要为了“问题很多”就机械升 major；版本号描述对使用者的兼容承诺，不描述工作量大小。

## 5. 版本字段对齐

正式 `1.2.2` 字段已对齐，由 `scripts/ci/assert_version_alignment.py` 守门；AI `/openapi.json` 从包装 metadata 读取，不再写死 `0.1.0`。Git tag 仍先打 `v1.2.2-rc.1`。

| 子项目 | 字段 |
| --- | --- |
| `frontend/package.json` + lock root | `1.2.2` |
| `backend-node/package.json` + lock root | `1.2.2` |
| `backend-java/pom.xml` | `1.2.2` |
| `gateway-java/pom.xml` | `1.2.2` |
| `backend-ai/pyproject.toml` + `uv.lock` project entry | `1.2.2` |
| `admin-frontend/package.json` + lock root | `1.2.2` |
| `admin-service/package.json` + lock root | `1.2.2` |
| `e2e/package.json` + lock root | `1.2.2` |
| AI `/openapi.json` info.version | 从 package metadata 自动得到 `1.2.2`，不再硬编码 `0.1.0` |

再加一个 CI version-alignment script，避免以后手工漏项。

## 6. Schema 与协议策略

当前 Java Flyway 最新为 V20（`inspection_task` 空库建表）。下一 migration 应从当前分支实际状态确定，可能包括：

- detection source ID 唯一键。
- outbox/inbox 表。
- 认证/审计所需字段（如 token family、mustChangePassword）属于 PostgreSQL Prisma migration。

所有 migration 均应 additive。release note 明确列出 MySQL Flyway 和 PostgreSQL Prisma 两套 migration，不能只写一边。

事件建议：

```json
{
  "schemaVersion": 2,
  "detectionId": "uuid",
  "eventTime": "2026-08-24T02:00:00Z"
}
```

先 consumer 双读，再 producer 切换。旧 v1 无 offset 时间在过渡期继续按明确的 Asia/Shanghai 解释。

## 7. Release candidate 门禁

### 7.1 代码与测试

- 各模块 lint/build/test 全绿。
- Admin 新增 auth/RBAC/log tests；Vue 新增生命周期 tests。
- 空 MySQL migration + PostgreSQL 并发 tests。
- v1/v2 event contract、时间跨日和幂等重投 tests。
- 恶意 PDF/图片/视频 corpus tests。
- 6 条现有业务 E2E 全跑，并新增 Admin E2E。

### 7.2 安全与依赖

- release SHA 上 npm/pip/Java/Trivy 扫描。
- 无未批准 P0/P1；advisory 豁免有适用性证据和到期日。
- operation log 历史处置完成，默认用户/密码无法登录。
- secrets preflight 通过，生产不含 known default，MQTT 不跳证书验证。
- SBOM、OCI digest 和 source SHA 归档。

### 7.3 fresh staging

- 使用新数据卷，不继承手工修过的 Keycloak/MySQL/PostgreSQL 状态。
- 真实 HTTPS domain 完成 OIDC PKCE、issuer、CORS、WebSocket。
- Flyway/Prisma migration 到目标版本；`ddl-auto=validate`。
- 告警、证据上传/预览/归档、AI PDF、Admin RBAC、logout/revoke 冒烟。
- 模拟 Rabbit/Keycloak/Temporal 短故障并恢复。
- 模拟中途部署失败，所有已升级服务恢复上一 manifest。

## 8. 镜像与 tag

建议同时保留三种标识：

```text
Git tag:       v1.2.2
Source SHA:    <40-char commit>
Image ref:     ghcr.io/<repo>/<service>@sha256:<digest>
Friendly tag:  main-<sha7>  # 仅作人类检索，部署以 digest 为准
```

禁止生产 `latest`。release manifest 要逐服务列 digest，因为七个应用镜像不是一个原子对象。

## 9. 回滚计划

1. 发布前记录上一份完整 manifest，不只记录一个假设所有服务相同的 tag。
2. MySQL/PostgreSQL 做一致性备份并验证可恢复。
3. migration 采用 expand/contract，确保上一镜像能在新 Schema 上运行。
4. 发布失败按整次部署逆序回滚，之后从公网执行冒烟。
5. 如果涉及数据语义回填，提供 forward-fix 优先方案；不要自动执行破坏性 down migration。
6. 回滚后确认 Rabbit/outbox、Temporal workflow 和 refresh token family 没有留在不兼容状态。

## 10. 建议 release note 结构

```markdown
# v1.2.2 发版说明

- 基于：v1.2.1 / release SHA
- 安全修复：日志秘密、RBAC、默认身份、依赖 advisory
- 正确性：时间、includeDeleted、Flyway
- 可靠性：幂等、会话状态机、发布回滚
- Schema：MySQL Vxx；PostgreSQL migration xxx
- 协议：v1/v2 兼容说明
- 已知限制与批准豁免
- 验收：CI、fresh staging、扫描、E2E、回滚演练链接
- 镜像 digest manifest
```

## 11. 最终一句话

**先按 P0 清单整改并验证，发 `v1.2.2-rc.1`；通过 fresh staging 且 P1 关闭或豁免后，再发布 `v1.2.2`。今天不要打生产 tag。**
