# 01. 现状、问题与架构决策

实施状态：**规划文档，认证方案未实施；当前实现只有注释变更**

## 1. 本文目的

本文先把“认证”和“授权”拆开，避免把两套登录页面简单理解为两套权限系统：

- 认证（Authentication）：确认“你是谁”，包括密码、MFA、OIDC 登录、令牌和会话。
- 授权（Authorization）：确认“你能做什么”，包括业务角色、后台角色、菜单和操作权限。
- 身份绑定（Identity Linking）：把外部身份提供方中的稳定主体映射到 SkyTrace 本地管理员记录。

当前业务前台和管理后台在这三个层面都存在差异。方案的目标是统一认证，不是取消业务面与管理面的授权隔离。

## 2. 当前组件与认证方式

| 入口/服务 | 当前认证方式 | 令牌/会话位置 | 主要授权来源 | 当前边界 |
| --- | --- | --- | --- | --- |
| `frontend` 业务 SPA | Keycloak OIDC Authorization Code + PKCE S256 | `keycloak-js` 内存状态 | Keycloak realm/client roles | 业务面 |
| `gateway-java` | OAuth2 Resource Server 验证 Keycloak JWT | 无服务端登录会话 | `ADMIN`、`OPERATOR`、`VIEWER` | `/api/**` |
| `backend-java` | OAuth2 Resource Server 再次验证 Keycloak JWT | Stateless | 同上 | 业务 API |
| `backend-node` | 自行读取 JWKS 并验证 Keycloak JWT | Stateless | 同上 | BFF、实时接口 |
| `admin-frontend` 管理 SPA | 自研用户名/密码登录 | access/refresh token 持久化到 `localStorage` | `/auth/me` 返回本地权限 | 管理面 |
| `admin-service` | Passport Local + HS256 JWT + PostgreSQL refresh token | `sys_refresh_token` | PostgreSQL Admin RBAC | `/admin-api/**` |

因此，当前不是“同一个身份源、两个授权域”，而是“两套身份源、两套会话、两套令牌生命周期，再加两套授权域”。后半部分——授权域隔离——是合理的；前半部分——重复维护凭据和会话——是主要技术债务。

## 3. 业务前台当前流程

### 3.1 登录

当前实现证据：

- `frontend/src/auth/keycloak.ts:11-15` 创建 `keycloak-js` 客户端，默认 realm 为 `skytrace`、client 为 `skytrace-web`。
- `frontend/src/auth/keycloak.ts:87-93` 使用 `login-required`、standard flow 和 `pkceMethod: 'S256'`。
- `deploy/keycloak/skytrace-realm.json:27-45` 将 `skytrace-web` 配置为 public client，开启 standard flow、关闭 direct grants，并要求 PKCE S256。
- `deploy/keycloak/skytrace-realm.json:48-56` 将 `skytrace-web` 加入 access token audience。

流程如下：

```text
浏览器访问业务前台
  -> keycloak-js 检查登录状态
  -> 未登录则跳转 Keycloak
  -> Keycloak 完成账号认证
  -> 浏览器携 authorization code 返回
  -> keycloak-js 使用 PKCE verifier 换取 token
  -> 业务请求附带 Authorization: Bearer <access token>
```

这条主流程方向正确，也符合公共浏览器客户端使用 Authorization Code + PKCE 的要求。

### 3.2 令牌刷新和注销

- `frontend/src/auth/keycloak.ts:60-76` 用唯一 `refreshPromise` 合并并发刷新。
- `frontend/src/auth/keycloak.ts:101-119` 在 API 调用前刷新将过期 token。
- `frontend/src/api/http.ts:24-39` 收到 401 后强制刷新一次，仍失败则重新认证。
- `frontend/src/auth/keycloak.ts:157-159` 通过 Keycloak logout 结束身份提供方会话。

业务前台没有把 token 写入自己的 `localStorage` store，这是比管理前台更安全的现状。

### 3.3 业务授权

- `frontend/src/auth/keycloak.ts:48-58` 当前前端只提取 `realm_access.roles`。
- `frontend/src/router/index.ts:41-56` 对聊天和审计页面做前端角色导航限制。
- `gateway-java` 和 `backend-java` 会再次校验 issuer、audience、过期时间和角色；前端路由守卫不是最终权限边界。
- `backend-node/src/auth/keycloak-jwt.service.ts:79-140` 校验 RS256 签名、issuer、audience、`exp`、`nbf`、`sub` 和业务角色。

需要保留的原则是：浏览器只做体验层隐藏；Gateway 和最终资源服务必须独立做认证与授权判断。

## 4. 管理后台当前流程

### 4.1 本地密码登录

当前实现证据：

- `admin-service/src/auth/auth.controller.ts:15-21` 的 `POST /admin-api/auth/login` 使用 `LocalAuthGuard`。
- `admin-service/src/auth/auth.service.ts:43-49` 按本地 username 查询 `sys_user` 并用 bcrypt 比较密码。
- `admin-service/prisma/schema.prisma:10-23` 在 PostgreSQL `sys_user` 中保存 username 和非空 password。
- `admin-service/prisma/seed.ts:79-96` 当前会建立公开固定凭据 `admin / Admin@123` 并输出到日志；这是独立的发布阻断项。

这意味着 Keycloak 中禁用某个同名用户，不会自动禁用 Admin Service 中的本地账号；反之亦然。

### 4.2 自签 access/refresh JWT

- `admin-service/src/auth/auth.module.ts:15-30` 用 `JWT_SECRET` 签发 15 分钟 access token。
- `admin-service/src/auth/auth.service.ts:52-65` access 和 refresh token 只放入本地 `user.id` 与 username。
- `admin-service/src/auth/auth.service.ts:68-103` 用独立 `JWT_REFRESH_SECRET` 验证 7 天 refresh token，并进行单次轮换。
- `admin-service/src/auth/auth.service.ts:105-112` 通过 token hash 删除单个或某用户全部 refresh session。
- `admin-service/src/auth/strategies/jwt.strategy.ts:24-36` 从 Bearer header 取本地 JWT，并再次检查本地用户状态。

当前本地 JWT 没有形成完整的 OIDC/OAuth 资源边界：代码没有显式验证 `iss`、`aud`、`azp` 或 token type，身份主键也与 Keycloak `sub` 不同。它可以在单服务内部工作，但需要团队长期自行维护密码策略、令牌策略、轮换、撤销、MFA、风控和账户恢复。

### 4.3 浏览器持久化与刷新

- `admin-frontend/src/store/auth.ts:17-35` 把 access token 和 refresh token 一起持久化到 `localStorage`。
- `admin-frontend/src/api/client.ts:19-23` 从 store 读取 access token 并附到请求。
- `admin-frontend/src/api/client.ts:25-71` 自行管理 401 刷新队列。
- `admin-frontend/src/store/auth.ts:25-29` 的注销请求未等待服务端撤销就清空 store 并跳转。

已确认的具体问题：

1. 任意同源 XSS 都可直接读取持续 7 天的 refresh token。
2. `admin-frontend/src/api/client.ts:42-49` 在无 refresh token 的 401 分支把 `isRefreshing` 置为 true 后提前返回，没有走 `finally`；后续 401 会永久排队。
3. 登出在 Axios 拦截器读取 token 前清空 store 时，`POST /auth/logout` 可能没有 Authorization，服务端 session 未撤销；API 又吞掉错误。
4. Admin Service 自己承担密码和 session 安全，但当前没有 Keycloak 已具备的 MFA、统一风控和账号恢复能力。

这些问题即使后续要迁移，也不能等待整个迁移结束才处理；它们属于 `v1.2.2` 的先行止血范围。

### 4.4 本地细粒度 RBAC

当前 Admin RBAC 有保留价值：

- `admin-service/prisma/schema.prisma:26-89` 定义 Role、Menu、UserRole 和 RoleMenu。
- `admin-service/src/common/permissions/permissions.guard.ts:25-60` 每次受保护操作从本地数据库解析角色和权限。
- `admin-service/src/auth/auth.service.ts:139-177` 的 `/auth/me` 返回角色、permission codes 和菜单树。
- `admin-frontend/src/App.tsx:31-62` 根据 `user:list`、`role:list` 等 permission code 控制页面体验。

这些 permission code 与后台菜单、按钮、业务动作紧密耦合。把它们全部复制进 Keycloak token 会产生两个问题：

- 权限修改要等 token 刷新才生效，撤权不够及时。
- Keycloak realm 配置会与 Admin Service 的菜单模型重复，形成新的双写。

因此，本方案不会把 PostgreSQL RBAC 整体迁到 Keycloak。

### 4.5 其他需要在迁移中一并收口的认证边界

- Admin guard 当前是 controller opt-in：users、roles、menus、logs 等各自添加 `JwtAuthGuard`；新 controller 若忘记添加会默认公开。目标应改为全局 fail-closed，只有 health、metrics 和 OIDC callback/login transaction 端点显式 public。
- `admin-service/src/auth/auth.service.ts:52-65` 的本地 JWT payload 没有随机 `jti`；同一用户在同一秒签发可能得到相同 refresh token，并与数据库 unique hash 冲突（唯一约束见 `admin-service/prisma/schema.prisma:70-78`）。
- 并发使用同一 refresh token 时，竞争失败方可能收到数据库异常，而不是稳定的“令牌已轮换/撤销”响应。
- `admin-service/src/auth/auth.controller.ts:29-34` 的 logout 同时要求 access token 和 body refresh token，但 service 只按 refresh hash 删除，没有校验两者属于同一用户（`admin-service/src/auth/auth.service.ts:105-108`）。
- 角色/菜单变更会通过本地 DB 查询较快生效，但本地 password 改变后既有 access token 仍可存活到 15 分钟；迁移文档不能把“删除 refresh row”描述为即时撤销所有 access token。
- `admin-service/src/main.ts:13` 当前启用宽泛 CORS；目标同源 BFF 不能沿用不加约束的跨源 credentials 策略。

这些问题在双轨期仍真实存在。能够被最终架构消除的，也需要在迁移测试中明确验证其退出，而不是仅删除前端按钮。

## 5. 当前 Keycloak 配置与管理面需求的差距

当前 realm 导入只有：

- realm roles：`ADMIN`、`OPERATOR`、`VIEWER`，见 `deploy/keycloak/skytrace-realm.json:9-23`。
- 业务 public client：`skytrace-web`，见 `:25-59`。
- 机器 client：`skytrace-service`，见 `:60-88`。
- 三个开发用户和一个服务账号，见 `:90-152`。

尚不存在：

- 独立的 Admin OIDC client。
- 管理 API 专用 audience。
- 管理入口专用 coarse role。
- 强制 MFA 的管理端 browser flow。
- Admin Service 的 Keycloak session、callback、identity binding 或 logout channel。
- 无开发用户的生产 realm 配置。

当前 `ADMIN` realm role 同时代表业务 `/api/admin/**` 权限。如果直接把它当作管理后台准入条件，会让“业务管理员”自动成为“平台身份与权限管理员”，扩大权限边界。因此新管理入口不能复用 `ADMIN` 作为唯一准入角色。

## 6. 风险排序

### P0：发布前止血

| 风险 | 当前证据 | 处置方向 |
| --- | --- | --- |
| 固定默认超级管理员密码 | `admin-service/prisma/seed.ts:79-96` | 移除固定凭据，排查已部署环境并轮换 |
| 认证请求秘密可能进入操作日志 | 总审计 `01-release-blockers.md` | 登录、刷新、改密、建用户字段强制脱敏，排查历史日志 |
| 管理 RBAC 可形成纵向提权 | 总审计 `01-release-blockers.md` | 在迁移前先修复 super role 保护和越权分配 |

### P1：认证调整主线

| 风险 | 影响 | 处置方向 |
| --- | --- | --- |
| 两套凭据与账号生命周期 | 禁用、离职、重置需要双处操作 | 统一到 Keycloak |
| refresh token 在 localStorage | XSS 后可长期接管 | BFF + HttpOnly server-side session |
| `skytrace-web` 与 Admin audience 未隔离 | token 横向复用风险 | 独立 `skytrace-admin-api` audience |
| 没有不可变外部身份绑定 | username/email 变化或冲突会绑错人 | 唯一 `(issuer, subject)` |
| 管理登录没有强制 MFA | 高权限账号保护不足 | 独立管理认证 flow，强制 WebAuthn/TOTP |
| 旧 JWT 只做本服务密钥验证 | 缺少标准 issuer/audience 边界 | 双轨后迁出本地 JWT |

### P2：防御纵深与长期治理

- 管理危险操作的 step-up authentication。
- Keycloak 高可用、realm 配置即代码和密钥轮换演练。
- 机器身份、用户身份和管理身份的 audience/scopes 进一步拆分。
- 高风险环境下独立 realm/实例及网络边界。

## 7. 架构决策

### ADR-AUTH-001：统一身份提供方

决策：所有常规用户凭据、MFA 和 SSO 由 Keycloak 管理；Admin Service 不再长期保存或校验日常登录密码。

理由：减少重复安全实现，使禁用、恢复、MFA、密码策略和会话治理集中生效。

不代表：业务和管理应用共用同一 client、audience 或权限集合。

### ADR-AUTH-002：管理端使用独立 client 和 audience

决策：

- OIDC client：`skytrace-admin`。
- 资源 audience：`skytrace-admin-api`。
- coarse resource client role：`resource_access.skytrace-admin-api.roles` 中的 `ADMIN_PORTAL_ACCESS`。
- `skytrace-web` token 不被 Admin Service 接受。
- Admin token 不因存在通用 `ADMIN` realm role 就自动访问所有业务写接口；是否跨域访问必须单独定义。

### ADR-AUTH-003：管理浏览器最终采用 BFF Cookie 会话

决策：Admin Service 作为 confidential OIDC client 完成 code exchange 和 refresh，浏览器只持有 opaque、HttpOnly、Secure Cookie。

理由：管理界面是高权限入口，且当前 Nginx 已把 SPA 和 `/admin-api` 置于同一入口，具备采用 BFF 的结构基础。RFC 10017 将完整 BFF 排在浏览器 OAuth 架构的更高安全级别，并明确其核心价值是 token 不暴露给浏览器 JavaScript。

过渡选项：若 BFF 实施被外部依赖阻塞，可以短期使用独立 public client + SPA PKCE，token 只能存内存，禁止进入 localStorage/sessionStorage；该选项必须有退出日期，不能成为新的长期双轨。

### ADR-AUTH-004：本地 RBAC 继续作为细粒度授权权威

决策：

```text
最终允许 = Keycloak 已认证
        AND token/client 属于 Admin 边界
        AND 外部身份已显式绑定
        AND 本地管理员 status=active
        AND 本地角色/权限满足操作要求
```

Keycloak 控制“能否进入管理门户”；PostgreSQL 控制“进入后能做什么”。任何一侧拒绝都必须拒绝。

### ADR-AUTH-005：不自动按 username/email 绑定高权限账号

决策：使用 `(issuer, subject)` 作为唯一外部身份键；现存管理员通过人工审核的映射清单或一次性受控绑定流程迁移。用户名、邮箱即使相同也不得自动绑定。

### ADR-AUTH-006：迁移采用 expand/dual/cutover/contract

决策：先新增身份绑定和新认证通道，再灰度前端；旧认证停止签发后保留短期验证窗口；完成会话撤销和观察后，最后移除旧接口、secret 和 password 字段。

禁止：同一发布中同时创建新 client、批量自动绑用户、切前端、撤销所有旧 token 并删除旧字段。

## 8. 明确不做的事情

- 不让两个 SPA 直接共享同一 client ID。
- 不让业务 token 访问 `/admin-api/**`。
- 不把全部菜单 permission code 塞进长生命周期 Keycloak token。
- 不按 username 或 email 静默创建/绑定管理员。
- 不把 Keycloak Admin Console 的超级权限授予普通 SkyTrace 管理员。
- 不保留公开默认本地管理员作为所谓“应急账号”。
- 不用 direct access grant/password grant 替代浏览器重定向登录。
- 不把 client secret 编译到 React/Vue 产物。
- 不在认证失败时自动切换到 permit-all 或宽松模式。

## 9. 决策前仍需确认的事实

以下信息必须在实施阶段 AUTH-001 中形成签字清单，但不阻碍当前方案排序：

1. `/admin-api/auth/login` 和 `/refresh` 是否存在 Admin SPA 以外的调用者。
2. 生产中实际管理员数量、账号所有者及最近登录时间。
3. 当前 `sys_user.username` 与 Keycloak 用户是否有可审核的一一对应关系。
4. 是否存在 SAML/LDAP/企业 IdP 联邦需求。
5. 管理域名与业务域名的最终 HTTPS origin。
6. 管理员 MFA 能否以 WebAuthn 为主、TOTP/恢复码为备。
7. 是否有法规要求把管理身份放入独立 realm 或独立 Keycloak 实例。

这些问题影响部署细节，不改变“统一身份、隔离 client/audience、保留本地 RBAC、显式身份绑定”的主决策。
