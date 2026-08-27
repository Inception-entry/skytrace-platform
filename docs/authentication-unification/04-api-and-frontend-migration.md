# 04. API、前端与兼容迁移

实施状态：**规划文档；API 和前端迁移未实施，相关源码/配置只有注释变更，依赖未改**

## 1. 迁移目标

迁移需要同时完成四件事：

1. 用户不再向 Admin SPA/Admin Service 提交日常登录密码。
2. 浏览器不再持有可读的 access/refresh token。
3. `/auth/me`、本地 user id、角色、菜单和 permission codes 尽量保持兼容。
4. 每一步都有可观测的双轨状态和明确回滚点。

只给 Admin 前端安装 OIDC SDK并不能完成迁移。若没有先完成身份绑定、服务端双轨验证、权限兼容和旧 session 撤销，会出现“Keycloak 登录成功但无法映射本地 userId”或“业务 token 误入管理 API”等问题。

## 2. 当前 API 清单与目标去向

当前全局前缀为 `/admin-api`，下表省略该前缀。

| 当前 API | 当前语义 | 双轨期 | 最终语义 |
| --- | --- | --- | --- |
| `POST /auth/login` | username/password 换本地 JWT | 保留，仅 legacy | 删除或稳定返回 410；浏览器走 OIDC 导航 |
| `POST /auth/refresh` | body 中 refresh token 换新 token | 保留，仅 legacy | 删除；BFF 在服务端刷新 |
| `POST /auth/logout` | Bearer + body refresh token，删除 DB row | 保留 legacy 语义 | 不复用以免歧义；由 `/auth/oidc/logout` 取代 |
| `GET /auth/me` | 按本地 user id 返回资料、角色、权限、菜单 | legacy/OIDC 都支持 | 保持响应兼容，Cookie session 认证 |
| `PUT /auth/profile` | 修改本地资料 | 两种认证都支持 | 只修改明确归 Admin DB 的资料 |
| `PUT /auth/password` | 校验/修改本地密码 | 仅 legacy | 删除/410；跳转 Keycloak account action |
| `POST /users` | 创建本地账号和 password | legacy 保留 | 改为本地授权档案 + 显式身份绑定，需版本化 |
| `PUT /users/:id` | 修改资料、password、status | legacy 保留 | 按字段所有权拆分，禁用需同步撤销 session |
| `DELETE /users/:id` | 删除本地用户 | 迁移前冻结高风险使用 | 默认禁用/tombstone，保留身份与审计 |

## 3. 拟议新增 BFF 接口

这些是建议契约，不是当前已有接口。

### 3.1 `GET /auth/session`

目的：SPA 启动时检查 BFF session，不触发登录重定向。

未登录建议返回 200，减少 401 噪声：

```json
{
  "authenticated": false,
  "login_url": "/admin-api/auth/oidc/login"
}
```

已登录：

```json
{
  "authenticated": true,
  "user": {
    "id": 17,
    "username": "alice",
    "nickname": "Alice"
  },
  "csrf_token": "opaque-per-session-value",
  "reauthentication_required_after": "2026-08-24T09:35:00Z"
}
```

约束：

- `Cache-Control: no-store`。
- 不返回 access、refresh、ID token、Keycloak session id 或 client secret。
- `csrf_token` 只用于 CSRF 绑定，不是登录凭据；XSS 仍可代表用户调用接口，所以 CSP 和输入输出安全仍必需。
- session 不存在时不能静默创建匿名持久 session，避免 session store 滥用。

### 3.2 `GET /auth/oidc/login`

Query：

```text
return_to=/admin/users
```

行为：

- 校验 `return_to` 是 `/` 开头的规范化站内路径。
- 拒绝 `//evil.example`、反斜杠、控制字符、绝对 scheme 和编码后绕过。
- 生成并服务端保存 state、nonce、PKCE verifier、创建时间和 return path。
- 302 到 Keycloak authorization endpoint。
- 支持明确的 `prompt=login`/step-up 入口，但客户端不能任意注入 OIDC 参数。

### 3.3 `GET /auth/oidc/callback`

行为：

1. 校验 state 一次性、未过期、绑定正确 issuer 和登录事务。
2. 用 confidential client authentication + PKCE code verifier 换 token。
3. 校验 ID token nonce、签名、issuer、audience、时间。
4. 校验 access token audience、client 和管理准入 role。
5. 按 `(issuer, subject)` 查 active binding 与 local user。
6. 建立新 server-side session；不复用登录前 session ID。
7. 写 Secure/HttpOnly Cookie。
8. 用 303 跳转到已校验的 return path，避免 callback 被重新 POST/缓存。

错误页必须区分：

- `OIDC_STATE_INVALID`
- `OIDC_CALLBACK_FAILED`
- `ADMIN_NOT_PROVISIONED`
- `ADMIN_BINDING_REVOKED`
- `ADMIN_DISABLED`
- `ADMIN_PORTAL_ACCESS_REQUIRED`
- `ADMIN_MFA_REQUIRED`

对外信息不泄露 token 或 IdP 内部错误；request ID 可供排查。

### 3.4 `POST /auth/oidc/logout`

请求：Cookie session + CSRF header，可选 JSON：

```json
{
  "scope": "current_admin_session",
  "return_to": "/logged-out"
}
```

允许的 scope 应是明确 enum：

- `current_admin_session`
- `all_admin_sessions`
- `global_sso`（只有产品确认后开放）

响应：本地 session 撤销和 Cookie 清除完成后返回 204 或 303。Keycloak logout 失败不得恢复本地 session；应记录可重试任务和提示“本地已退出”。

### 3.5 `GET /auth/me`

保持当前主要响应形状：

```json
{
  "id": 17,
  "username": "alice",
  "nickname": "Alice",
  "email": "alice@example.com",
  "avatar": null,
  "roles": [{ "id": 1, "name": "超级管理员", "code": "super_admin" }],
  "permissions": ["user:list", "role:list"],
  "menus": []
}
```

兼容原则：

- OIDC principal 先映射为本地 user id，再复用 RBAC 查询。
- 不把 Keycloak realm/client roles 混入 `roles` 数组，避免前端误认。
- 可在 additive 新字段中提供 `auth_source`、`mfa`，但旧前端应能忽略。
- 401 表示无有效 session；403 表示已认证但 binding/user/portal access 被拒绝。

### 3.6 recent authentication / step-up

高风险操作可以返回：

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "code": "RECENT_AUTHENTICATION_REQUIRED",
  "reauthentication_url": "/admin-api/auth/oidc/login?purpose=step-up&return_to=%2Fadmin%2Froles"
}
```

前端只导航到服务端提供的同源 URL。完成 step-up 后，服务端更新当前 session 的 `auth_time/acr`，并轮换 session ID；不要接受浏览器自己声称“已完成 MFA”。

## 4. 旧 API 的弃用策略

### 4.1 双轨期 Header

旧接口在兼容阶段可返回：

```http
Deprecation: true
Sunset: <approved HTTP date>
Link: </docs/auth-migration>; rel="deprecation"
```

`Sunset` 日期只有在调用者清单完成、负责人批准后填写，不能在文档中虚构日期。

### 4.2 停止签发早于停止验证

顺序必须是：

```text
停止 UI 使用 legacy
  -> 停止 POST /auth/login 签发新 token
  -> 停止 POST /auth/refresh 续期
  -> 继续短期验证已签发 access token
  -> 主动撤销所有 legacy refresh rows
  -> 等待最大 access TTL + 缓冲
  -> 关闭 legacy validator
  -> 最后删除 API/secret/schema
```

若先关闭验证，正在执行管理操作的用户会突然全部 401；若一直允许 refresh，旧体系永远无法自然归零。

### 4.3 410 与 404

契约正式移除后，建议在一个明确弃用版本中返回稳定 410 和迁移错误码，比立即 404 更便于识别旧客户端。长期无调用后再移除 route。若接口从未对外承诺且确认只有同步发布的 SPA 调用，可以缩短该阶段，但必须有日志证据。

## 5. Admin 前端目标状态机

当前 `RequireAuth` 只检查 token 字符串是否存在。目标应是显式状态机：

```text
booting
  -> anonymous
  -> redirecting
  -> authenticated
  -> not_provisioned
  -> forbidden
  -> expired
  -> unavailable
```

状态含义：

| 状态 | UI | 可发业务 API |
| --- | --- | --- |
| `booting` | 明确 skeleton/“正在检查会话” | 否 |
| `anonymous` | 登录按钮或自动导航 | 否 |
| `redirecting` | 防重复点击，保存 return path | 否 |
| `authenticated` | 正常后台 | 是 |
| `not_provisioned` | 显示当前统一身份和申请方式 | 否 |
| `forbidden` | 显示缺权限，不循环登录 | 否 |
| `expired` | 提示会话过期并重新登录 | 否 |
| `unavailable` | IdP/BFF 故障页与 request ID | 否 |

## 6. 前端数据存储

### 必须移除

- `skytrace-admin-auth` 中的 access token。
- `skytrace-admin-auth` 中的 refresh token。
- 任何 sessionStorage、IndexedDB 或 URL fragment 中的 JWT。
- 前端自行维护的 refresh queue。

### 可以保存

- 主题、语言、布局偏好。
- 不敏感的最近访问页；恢复前必须校验站内路径。
- 用户资料的短期内存副本。
- CSRF token 的内存副本，页面刷新后从 `/auth/session` 重新获取。

OIDC/BFF 新版本首次启动时应主动清除旧 `skytrace-admin-auth`。仅清浏览器存储不等于服务端撤销，切换步骤还必须清理 `sys_refresh_token`。

## 7. Axios/fetch 行为

BFF 目标中：

- 同源请求自动携带 HttpOnly Cookie，JavaScript 不设置 Authorization。
- 所有状态变更请求携 `X-SkyTrace-CSRF`，值来自当前 session。
- 401 不调用本地 refresh API；先调用 `/auth/session` 判定，再进入单一登录恢复流程。
- 403 不重新登录，展示权限/绑定错误。
- 503 展示认证依赖不可用，带有有界重试。
- 请求 helper 必须拒绝向跨 origin URL附加 CSRF 或其他敏感 header。
- 头像上传也使用同一 client，不允许旁路认证/错误处理。

并发恢复采用一个 `recoveryPromise`，所有调用共享；每条路径都 consume rejection 并复位。多标签 logout 可使用 `BroadcastChannel('skytrace-admin-session')` 同步 UI，但真正权限仍由 server-side session 决定。

## 8. 登录页与账号 UX

### 8.1 登录页

最终登录页不再渲染 username/password 表单，只提供：

- “使用统一身份登录”。
- IdP 不可用时的可重试状态。
- 已登录但未绑定时的身份信息、申请方式和“切换账号”。
- 会话过期原因。
- 不泄露本地用户是否存在的通用错误。

### 8.2 密码管理

- “修改密码”导航到 Keycloak account console 或由服务端生成的 required-action URL。
- 不把现有密码或新密码传给 Admin Service。
- 管理员为他人重置密码时，优先触发 Keycloak required action，而非设置可知的固定密码。
- 任何 Keycloak Admin API 集成都必须在服务端，使用最小权限独立 client。

### 8.3 新增用户

目标流程将“身份创建”和“本地授权档案”显式拆开：

```text
已有 Keycloak 人员
  -> 查询/选择稳定 subject
  -> 创建或选择本地 User 授权档案
  -> 双人审核（super admin）
  -> 建立 binding
  -> 分配本地角色
```

`v1.3.0` 可以先采用“IdP 侧人工建人 + Admin 侧显式绑定”，避免首版就给 Admin Service 过大的 Keycloak 管理权限。自动 provisioning、outbox/reconciliation 可作为后续能力。

## 9. 业务前端和 Node/Java 的影响

Admin BFF 迁移本身不要求修改业务前台登录流。必须守住以下边界：

- `frontend` 继续用 `skytrace-web` PKCE。
- `gateway-java`、`backend-java`、`backend-node` 继续只接受业务 audience。
- `backend-node` 继续将业务 Bearer token 传给 Java。
- 新 Admin token/session 不直接调用业务 `/api/**`。
- 主业务 SPA 当前 `/audit`/`/admin` 页面需要产品确认：若属于真正管理能力，应逐步迁到 Admin portal；若只是业务审计视图，可保留业务 `ADMIN` 语义，但不能因此获得 Admin PostgreSQL 管理权限。

业务 audience 从当前 `skytrace-web` 改为独立资源名是后续工作，不能与 Admin 首次切换打包，见 [08. 后续演进方向](08-future-directions.md)。

## 10. 配置模式

建议服务端使用必填、显式模式：

```text
ADMIN_AUTH_MODE=legacy | dual | oidc-bff
```

建议前端运行时配置：

```json
{
  "authMode": "legacy | oidc-bff",
  "adminApiBaseUrl": "/admin-api"
}
```

BFF 模式不需要把 Keycloak client secret 或 token endpoint secret 暴露给前端。Keycloak public URL 可以用于展示/故障提示，但授权请求应由 `/auth/oidc/login` 服务端生成。

规则：

- 生产缺 `ADMIN_AUTH_MODE` 时启动失败，不能默认 legacy 或 permit-all。
- 前端和服务端模式不匹配时部署 preflight 失败。
- OIDC 失败时不能自动降级到 legacy 密码表单。
- `dual` 仅在服务端存在，前端每个发布实例只使用一种确定的登录方式。
- 模式切换必须进入审计日志与发布记录。

## 11. 分阶段迁移

### Phase 0：止血与盘点

- 修复密码/token 日志泄漏、固定 seed、RBAC 提权、刷新死锁和注销竞态。
- 盘点旧 API 调用者、active users、sessions 和角色快照。
- 冻结新的共享账号和默认账号创建。

退出条件：P0 止血项通过测试；调用者和账号清单有负责人确认。

### Phase 1：Keycloak 与数据层 expand

- 创建 `skytrace-admin` confidential client、`skytrace-admin-api` audience、管理准入 role 和 MFA flow。
- 新增 identity binding、OIDC session 和必要审计字段。
- 生产 Keycloak desired state 不包含开发用户。

退出条件：真实 HTTPS callback、MFA、错误 redirect、备份恢复均验证。

### Phase 2：Admin Service dual

- 新增 BFF login/callback/session/logout。
- 保留 legacy validator/endpoints。
- 按 `(iss, sub)` 映射 local user，复用现有 RBAC。
- 增加 auth source metrics 和错误码。

退出条件：dual token/session 矩阵全绿；旧 frontend 不受影响。

### Phase 3：账号绑定与内部灰度

- 导入审批后的 binding。
- 至少两个 super admin 完成 Keycloak MFA 和 BFF 登录。
- 比对 legacy 与 OIDC `/auth/me` 权限快照。

退出条件：活跃用户映射完整、零冲突、零权限意外扩大。

### Phase 4：Admin 前端切换

- 运行时 `authMode=oidc-bff`。
- 移除密码表单和前端 token persistence。
- 清除旧 localStorage key。
- 灰度从内部账号到全量。

退出条件：登录、深链、并发、注销、多标签、Keycloak 故障 E2E 通过。

### Phase 5：停止 legacy 签发

- 登录端点停止新 token。
- refresh 端点停止续期。
- 主动撤销/删除 legacy refresh rows。
- 保留短期 legacy access validation 和受控回滚能力。

退出条件：legacy 命中归零，经过最大 access TTL 和安全缓冲。

### Phase 6：关闭 legacy 验证

- `ADMIN_AUTH_MODE=oidc-bff`。
- 移除正常 UI 中的 legacy 入口。
- 轮换/撤出 legacy secrets。

退出条件：至少一个稳定发布周期、无未登记旧客户端。

### Phase 7：contract

- 删除 legacy login/refresh/password 代码。
- 清除 password hashes 和旧 session 表。
- 按版本政策删除旧 API/Schema。

退出条件：上一稳定镜像不再被快速回滚依赖；灾难恢复备份已验证。

## 12. 临时 SPA PKCE 桥接方案

如果 Phase 2 的 BFF session 依赖确实阻塞，而身份统一必须先完成，可在单独 ADR 批准后使用：

```text
admin-frontend -> public client skytrace-admin-spa-transition
              -> Authorization Code + PKCE S256
              -> token 仅内存
              -> Bearer aud=skytrace-admin-api
              -> Admin Service Resource Server
```

限制：

- 不用 `skytrace-admin` confidential client，也不把它改成 public。
- 不使用 localStorage/sessionStorage/IndexedDB 保存 token。
- Admin Service 仍按 `(iss, sub)` 和本地 RBAC 授权。
- 设置 owner、退出日期、迁移到 BFF 的验收 issue。
- BFF 上线时先让服务端 dual 支持 Cookie/Bearer，再切前端。

不接受“先用 localStorage 方便一下”的过渡方案，因为那会重复当前最高风险。

## 13. API 兼容验收

- 旧 frontend + dual Admin Service：现有登录、刷新、`/me` 可用。
- 新 frontend + dual Admin Service：BFF session、`/me` 可用。
- 新 frontend + legacy-only service：部署 preflight 阻止，不允许上线后才发现。
- 旧 frontend + oidc-bff-only service：部署 preflight 阻止。
- 两种来源的 `/auth/me` 对相同本地 user 返回相同角色、权限、菜单。
- OIDC identity username 改名不改变 local user id。
- 未绑定/disabled/缺 role 的 401/403 不进入重定向循环。
- 旧 refresh 停止签发后，数据库行数单调下降至零。
- legacy validator 关闭后，任何本地 HS token 都被拒绝。
- 最终浏览器 storage 和网络响应中都没有 access/refresh token。
