# 03. 身份绑定、权限与数据模型

实施状态：**规划文档；以下 Schema、SQL 和接口均为建议，没有创建 migration**

## 1. 数据设计目标

数据层需要同时满足：

- Keycloak 身份可以改用户名、邮箱和展示名，但不会错绑本地管理员。
- 一个本地管理员可以在未来绑定多个受信身份提供方，而不必改 `sys_user` 主键。
- 管理员禁用和权限撤销能够快速生效，不必等待 Keycloak access token 过期。
- 双轨期可区分 legacy 与 OIDC session，能够精确撤销和统计。
- 回滚时保留旧字段，切换稳定后再 contract。
- 数据库泄露时不直接暴露可复用的浏览器 session ID 或明文 Keycloak refresh token。

## 2. 当前数据模型

当前 `admin-service/prisma/schema.prisma` 中：

- `User` 以本地自增 `id` 为主键，`username` 唯一，`password` 非空。
- `UserRole` 将本地 user 绑定到 `Role`。
- `RoleMenu` 将角色绑定到 `Menu`，Menu code 同时承担 permission code。
- `RefreshToken` 保存本地 refresh JWT 的 SHA-256 hash、userId 和 expiresAt。
- `OperationLog` 只保留本地 userId 和 username，没有外部 issuer/subject/session 标识。

应保留 `User.id` 作为 Admin 领域内部主键。不要把所有外键改成 Keycloak `sub`；外部身份应该通过独立 binding 表接入。

## 3. 推荐身份模型

### 3.1 独立 IdentityBinding 表

推荐新增独立映射，而不是只在 `User` 上增加一个 `keycloakSubject`：

```prisma
// 仅为拟议模型，不是当前仓库代码。
model IdentityBinding {
  id              Int       @id @default(autoincrement())
  userId          Int       @map("user_id")
  issuer          String
  subject         String
  provider        String    @default("keycloak")
  usernameSnapshot String?  @map("username_snapshot")
  emailSnapshot   String?   @map("email_snapshot")
  status          Int       @default(1)
  boundAt         DateTime  @default(now()) @map("bound_at")
  boundByUserId   Int?      @map("bound_by_user_id")
  lastLoginAt     DateTime? @map("last_login_at")
  revokedAt       DateTime? @map("revoked_at")
  revokedByUserId Int?      @map("revoked_by_user_id")
  reason          String?
  user            User      @relation(fields: [userId], references: [id], onDelete: Restrict)

  @@unique([issuer, subject])
  @@index([userId, status])
  @@map("sys_identity_binding")
}
```

设计含义：

- `issuer + subject` 是外部身份全局键。只保存 `subject` 不足以区分未来不同 realm/issuer。
- `usernameSnapshot`、`emailSnapshot` 用于审计和展示，不能参与登录绑定决策。
- `status/revokedAt` 允许撤销绑定但保留历史证据。
- `onDelete: Restrict` 防止删除用户时无意抹掉身份绑定审计；实际删除采用禁用/匿名化策略。
- 同一个外部主体只能绑定一个本地用户。
- 是否允许一个本地用户绑定多个主体需要明确策略；推荐普通管理员最多一个 active 主体，迁移/IdP 更换时通过受控例外放宽。

### 3.2 为什么不按 username/email 自动绑定

以下场景会导致错误或提权：

- Keycloak username 被重命名或复用。
- 邮箱大小写、别名、域迁移或未验证状态变化。
- 本地存在 `admin`，Keycloak 也存在同名开发账号，但所有者并非同一人。
- 联邦 IdP 对同一邮箱产生不同 `sub`。
- 攻击者先注册/控制与本地管理员相同的邮箱或 username。

因此，首次登录只允许三种结果：

```text
绑定存在且 active -> 继续
绑定存在但 revoked -> 拒绝并告警
绑定不存在 -> 拒绝或进入人工待审批，不自动建管理员
```

### 3.3 绑定输入

批量迁移清单至少包含：

| 字段 | 用途 | 是否可作为唯一身份依据 |
| --- | --- | --- |
| local_user_id | 现有 Admin user | 是，本地侧 |
| keycloak_issuer | 防止跨 realm 冲突 | 是，外部键一部分 |
| keycloak_subject | 不可变主体 | 是，外部键一部分 |
| keycloak_username | 人工核对 | 否 |
| keycloak_email | 人工核对 | 否 |
| owner/department | 审批证据 | 否 |
| approved_by | 双人复核 | 审计字段 |
| approved_at | 审计 | 审计字段 |
| local_roles | 迁移后预期角色 | 授权核对 |

Keycloak `sub` 应通过管理 API/受控导出获得，不能让操作人员手工抄写长字符串后直接导入而无二次核验。

## 4. 推荐 Admin OIDC Session 模型

### 4.1 独立 session 表

不要复用 `sys_refresh_token` 的语义。旧表存的是本地 JWT hash；新表存的是 BFF session 与加密的 Keycloak token。推荐 additive 新表：

```prisma
// 仅为拟议模型，不是当前仓库代码。
model OidcSession {
  id                    BigInt    @id @default(autoincrement())
  sessionIdHash         String    @unique @map("session_id_hash")
  userId                Int       @map("user_id")
  identityBindingId     Int       @map("identity_binding_id")
  issuer                String
  subject               String
  keycloakSid           String?   @map("keycloak_sid")
  accessTokenCiphertext Bytes?    @map("access_token_ciphertext")
  refreshTokenCiphertext Bytes?   @map("refresh_token_ciphertext")
  idTokenCiphertext     Bytes?    @map("id_token_ciphertext")
  encryptionKeyVersion  String    @map("encryption_key_version")
  accessExpiresAt       DateTime? @map("access_expires_at")
  idleExpiresAt         DateTime  @map("idle_expires_at")
  absoluteExpiresAt     DateTime  @map("absolute_expires_at")
  csrfSecretHash        String    @map("csrf_secret_hash")
  authTime              DateTime? @map("auth_time")
  acr                    String?
  ipPrefixHash          String?   @map("ip_prefix_hash")
  userAgentHash         String?   @map("user_agent_hash")
  permissionVersion     BigInt?   @map("permission_version")
  createdAt             DateTime  @default(now()) @map("created_at")
  lastSeenAt            DateTime  @default(now()) @map("last_seen_at")
  revokedAt             DateTime? @map("revoked_at")
  revokeReason          String?   @map("revoke_reason")

  @@index([userId, revokedAt])
  @@index([keycloakSid])
  @@index([absoluteExpiresAt])
  @@map("sys_oidc_session")
}
```

模型中的 token ciphertext 字段是否全部需要，取决于采用的 OIDC library 和 logout 语义；实施 ADR 应按最少保存原则裁剪。不能把示例当成“所有字段必须落库”。

### 4.2 存储规则

- 浏览器得到随机 session ID；数据库只保存不可逆 hash。
- access/refresh/id token 如需持久化，必须使用带认证的加密（AEAD），而非 base64 或单纯 hash。
- 加密密钥不进入 Git、镜像、数据库或普通 Compose 默认值；记录 key version 以支持轮换。
- 日志和 metrics 不记录 Cookie、session ID、authorization code、access token、refresh token、ID token、client secret、PKCE verifier 或 CSRF secret。
- session 备份与加密密钥备份分开控制。
- 清理任务删除已过期/revoked session 的 token ciphertext，但按审计保留期保留最小元数据。

### 4.3 并发刷新

同一 session 多请求并发时必须保证只有一个 refresh：

```text
读取 session version
  -> 若 access token 仍有安全余量，直接继续
  -> 否则获取 session 级锁/compare-and-swap
  -> 再读一次 token 状态
  -> 只有赢家请求 Keycloak refresh
  -> 原子写回新 token、expiry、version
  -> 其他请求读取新状态
```

数据库实现可选：

- `SELECT ... FOR UPDATE`，锁只覆盖 token refresh，不覆盖下游业务调用。
- 乐观锁 `version` + 有界重试。
- Redis 分布式 single-flight；只有在 Admin Service 多实例且数据库锁无法满足时引入。

刷新失败分类：

| 失败 | 动作 |
| --- | --- |
| `invalid_grant`/session revoked | 立即撤销本地 session，401 |
| Keycloak 5xx/timeout | 短期、有限重试；不可变成认证成功 |
| unknown `kid` | 刷新 JWKS 一次，再失败则拒绝 |
| 数据库 CAS 冲突 | 读取赢家结果，不重复使用旧 refresh token |
| 解密失败/key version 不可用 | 撤销 session、P1 告警，不输出 token 数据 |

## 5. Local User 调整

### 5.1 Expand 阶段

初期保留现有字段，新增关系和迁移状态：

- `password` 暂时保持非空，旧认证仍可回滚。
- 新增 identity bindings。
- 可新增 `authMode`/`legacyLoginDisabledAt` 等显式状态；也可用系统级 auth mode 配合，不要靠 password 是否为空猜测。
- 不更改 `User.id`，所有现有 UserRole/OperationLog 外键继续有效。

### 5.2 Cutover 后

- OIDC 已绑定用户禁止本地密码登录。
- 用户资料拆分所有权：
  - username、email 等主身份属性以 Keycloak 为源，Admin DB 只存 snapshot/业务展示覆盖。
  - avatar、nickname 是否本地可编辑，需要产品决策。
- 管理员禁用可在 Keycloak 和本地任一侧触发；本地 status 是额外 deny，不是 Keycloak 的替代品。

### 5.3 Contract 阶段

满足所有移除门槛后：

- `User.password` 先改 nullable，再清除遗留 hash，最后删除字段；每一步单独 migration。
- 删除 `RefreshToken` 表或只保留已匿名化迁移审计；不要在首次切换发布中删除。
- 移除 `JWT_SECRET`、`JWT_REFRESH_SECRET` 配置和本地签发代码。
- 所有 contract migration 必须确认上一稳定镜像不再需要旧字段，否则无法安全回滚。

## 6. 权限来源与命名

### 6.1 三层角色

| 层级 | 示例 | 来源 | 用途 |
| --- | --- | --- | --- |
| 业务平台角色 | `ADMIN`、`OPERATOR`、`VIEWER` | Keycloak realm/client role | 业务 `/api/**` 粗粒度能力 |
| 管理门户准入 | `ADMIN_PORTAL_ACCESS` | `skytrace-admin-api` resource client role | 是否允许建立 Admin session |
| 管理细粒度角色 | `super_admin`、未来 `user_admin` | Admin PostgreSQL | 菜单与 endpoint permissions |

命名必须刻意不同。不要把 Keycloak `ADMIN`、Admin DB `super_admin` 和“Keycloak 管理控制台管理员”混为一个角色。

### 6.2 权限决策

现有 permission codes 可以保留：

- `user:list/create/update/delete/assign-roles`
- `role:list/create/update/delete/assign-menus`
- `menu:list/create/update/delete`
- `log:list/clear`

但必须修复总审计已指出的 super role 保护：

- 非 `super_admin` 不能给任何用户分配 `super_admin`。
- 非 `super_admin` 不能修改、禁用、删除 `super_admin` role。
- 不能删除或禁用最后一个有效 `super_admin`。
- 用户不能通过修改自身角色/状态绕过限制。
- 角色分配和“最后一个 super admin”检查要在事务和并发控制下完成。
- 高风险变更要求 recent MFA、二次确认和完整审计。

### 6.3 权限缓存

若引入缓存：

- 权限真相仍在 PostgreSQL。
- cache key 包含 local user id 和 `permissionVersion`。
- 角色/菜单/用户状态变化时先提交数据库，再失效缓存。
- 缓存不可用时，管理写操作回源数据库；不能拿旧缓存“维持可用”而越权。
- disabled、binding revoked、super_admin 变化不得使用长 TTL。

可在 `User` 或独立表中维护递增 `permissionVersion`。session 记录登录时版本，版本不一致时强制回源并更新，而不是继续使用旧权限。

## 7. 管理员迁移分类

实施前将现有 `sys_user` 分类：

| 类别 | 条件 | 动作 |
| --- | --- | --- |
| A：活跃且确认所有者 | 近期有合法使用、有对应 Keycloak 人员 | 人工审核后绑定 |
| B：活跃但无 Keycloak 身份 | 仍有业务需要 | 先在 IdP 按正式流程建人，再绑定 |
| C：长期未使用 | 超过组织定义阈值 | 先禁用，不为迁移而激活 |
| D：共享账号 | 多人共用一个 username | 禁止直接迁移；拆为实名账号 |
| E：机器人/脚本 | 非人类调用 | 迁移到独立 machine client，不绑定人类 Admin session |
| F：默认/测试账号 | seed 或 demo 身份 | 生产删除/禁用并排查使用历史 |

共享账号、默认账号和服务账号不能伪装成普通 OIDC 人类用户。

## 8. 建议绑定流程

### 8.1 批量预绑定

适合生产切换前：

1. 导出现有 active Admin users、roles、最后登录和 owner。
2. 从 Keycloak 受控导出 issuer、sub、username、email、MFA enrolled 状态。
3. 由业务负责人匹配，安全负责人复核；super admin 使用双人审批。
4. 运行 dry-run，只报告 missing、duplicate、collision、disabled，不写库。
5. 保存签字后的清单摘要和校验和。
6. 在维护窗口执行 additive binding 导入。
7. 对每条记录验证唯一约束和本地角色未改变。

### 8.2 首次登录审批

若必须支持少量 JIT 绑定：

- 未绑定登录只创建 `pending_binding_request`，不能创建 active user/session。
- 请求展示 Keycloak subject、已验证 email、组织信息和发起时间。
- 由已有具备 `user:bind-identity` 权限且完成 recent MFA 的管理员批准。
- 审批者不能批准自己；super admin 绑定要求第二审批者。
- 一次性请求有短 TTL，绑定成功后不可重放。

生产不推荐开放任意用户自助 JIT provisioning。

## 9. 解除绑定、改名与离职

### 改 username/email

- 只更新 snapshot。
- `(issuer, subject)` 不变，不影响权限和 session。
- 变更写审计事件，但不触发新绑定。

### 解除绑定

- 先撤销 binding，再撤销该 binding 的全部 Admin sessions。
- 不删除历史 binding 记录。
- 若该用户是最后一个 `super_admin`，必须拒绝或先完成受控交接。

### 离职/禁用

推荐事件顺序：

1. Keycloak/企业 IdP 禁用。
2. Admin 本地 user status 置 disabled 或 binding revoked。
3. 撤销全部本地 OIDC sessions。
4. 请求 Keycloak 注销相关 sessions。
5. 记录操作者、原因、ticket 和结果。

任何单步失败都应重试并告警；本地 deny 应优先立即生效。

## 10. 操作日志身份字段

现有 `OperationLog` 最终建议增加或通过结构化日志补充：

| 字段 | 说明 | 敏感性 |
| --- | --- | --- |
| `actor_user_id` | 本地 Admin user id | 内部标识 |
| `actor_issuer` | 规范化 issuer | 内部配置 |
| `actor_subject` | Keycloak immutable subject | 个人标识，受访问控制 |
| `actor_username_snapshot` | 当时展示名 | 可变，仅审计展示 |
| `auth_source` | `legacy` / `oidc_bff` / `machine` | 普通 |
| `session_fingerprint` | session ID 的不可逆截断 HMAC | 仅关联，不可登录 |
| `keycloak_sid_hash` | 可选，关联 SSO session | 仅关联 |
| `acr` / `mfa` | 认证强度 | 普通安全元数据 |
| `request_id` / `trace_id` | 跨服务排查 | 普通 |

绝对禁止写入 operation log：password、currentPassword、newPassword、authorization code、Cookie、Bearer header、access token、refresh token、ID token、client secret、PKCE verifier、CSRF secret。

## 11. 数据迁移顺序

```text
M1 备份并统计现有用户/角色/session
M2 新增 identity_binding 与 oidc_session（additive）
M3 导入经过审批的绑定；业务数据不变
M4 开启 dual auth，新 session 标记 auth_source
M5 前端灰度；对比 legacy/OIDC 权限快照
M6 禁止 OIDC 已绑定用户再用本地密码登录
M7 停止签发 legacy refresh token
M8 等待旧 token 最大 TTL + 安全缓冲，随后全量撤销
M9 清除 password hash、旧 refresh token 数据
M10 在独立后续版本删除字段/表和 secret
```

M2–M8 都必须保持上一稳定版本可读，M9/M10 属于难回滚 contract 阶段。

## 12. 数据验收条件

- 所有 active 管理员都有唯一、已审批 `(issuer, subject)`，或明确列为不迁移并已禁用。
- 零个 subject 绑定多个 local users。
- 零个 local shared/default/test account 处于生产 active 状态。
- 迁移前后每个用户的本地 roles/permissions 集合一致；差异逐条签字。
- OIDC 登录改名后仍映射同一 local user。
- 未绑定、revoked binding、disabled local user 都不能建立 session。
- session 表中没有明文 token 或原始 Cookie session ID。
- 清理、撤销和 encryption key rotation 均有自动测试和演练记录。
- 最后一个 `super_admin` 在并发修改下仍受保护。
