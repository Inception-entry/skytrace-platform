# 05. 安全基线与运维要求

实施状态：**规划文档；安全方案未实施，Nginx、Compose 和监控文件只有注释变更，Keycloak 严格 JSON 与 Secret 未改**

## 1. 安全目标

认证统一后，Keycloak 会成为更重要的共同依赖。减少两套认证代码不代表风险自动消失，而是把风险集中到更清晰、可治理的位置。上线必须同时完成：

- 身份提供方加固。
- Admin client/audience 隔离。
- BFF Cookie、CSRF 和 session 安全。
- 服务端 token 严格验证。
- 浏览器 XSS 防护。
- 配置即代码、备份、监控、轮换与应急演练。

## 2. Keycloak 环境基线

### 2.1 开发与生产 realm 分离

当前 `deploy/keycloak/skytrace-realm.json:90-152` 包含三个开发用户，三者使用同一个 `${SKYTRACE_DEV_USER_PASSWORD}`，且密码不是 temporary。生产 overlay 在 `deploy/docker-compose.production.yml:42-54` 继续通过 `--import-realm` 启动。

生产要求：

- production desired state 不包含任何测试用户、固定密码或本地演示邮箱。
- `skytrace-admin`、`skytrace-operator`、`skytrace-viewer` 在所有现存生产环境中盘点、禁用/删除并检查登录历史。
- 测试用户同步脚本只能在明确 local/CI 环境执行，生产凭环境守卫直接拒绝。
- 初始管理员通过受控引导流程创建：一次性临时凭据、强制 MFA、首次登录改密，并由第二人复核。
- Keycloak bootstrap admin 只用于 IdP 运维，不作为 SkyTrace 应用日常管理员。

### 2.2 `--import-realm` 不是升级机制

生产 realm 已存在后，修改 JSON 再重启不能被当作可靠变更流程。目标：

- 用版本化、幂等的 Keycloak migration 管理 clients、scopes、mappers、roles、flows 和 redirect URI。
- 工具可选 kcadm、Terraform provider、Operator 或团队现有 IaC；选型需要 ADR。
- 每次变更先导出 realm desired/current diff，人工审批后执行。
- CI/部署 preflight 比较实际配置与 desired state。
- 禁止为“重新导入 JSON”删除生产 realm 或数据库。

### 2.3 Admin client 基线

`skytrace-admin`：

- confidential client；secret 或更强 client authentication 仅在 Admin Service。
- Standard Authorization Code flow 开启。
- PKCE S256 开启，即使 confidential client 也保留。
- implicit flow、direct access grant、device grant（无明确需求时）、service account 均关闭。
- `fullScopeAllowed=false`。
- redirect URI 精确到 callback，不使用 `https://admin.example.com/*` 作为最终生产配置。
- post-logout redirect URI 和 web origin 精确配置。
- 只请求必要 scopes：`openid profile email` 及明确 Admin resource scope；默认不发 offline access。
- access token audience 仅包含 `skytrace-admin-api` 和协议所需 audience，不包含业务 API。
- 管理准入 role 位于 `skytrace-admin-api` resource client，claim 路径为 `resource_access.skytrace-admin-api.roles`，不复用 realm `ADMIN`。

如果未来建立 `skytrace-admin-automation`，必须与 browser client 分开、关闭 browser flow，并仅授予最小机器权限。

### 2.4 登录策略

Admin client 使用独立 browser authentication flow 或 client flow override：

- 强制第二因素，推荐 WebAuthn；TOTP 与恢复码作为受控备选。
- 禁止只因用户已登录业务 SSO 就跳过管理 MFA；SSO 可以复用第一因素，但必须满足管理 `acr`。
- 启用 brute-force detection、失败退避和账号告警。
- 密码策略、泄露密码检查和恢复流程以组织政策为准。
- 关闭长期 remember-me，或为 Admin client 使用更短、明确的策略。
- 恢复码一次性、可撤销，并提示安全存储。
- MFA 重置属于高风险操作，需要身份核验、双人审批和审计。

当前 realm JSON 没有显式管理端 MFA、brute-force 和 session policy；不能假设 Keycloak 默认值满足生产要求。

## 3. Session 与 Cookie 基线

### 3.1 Cookie

推荐名称：

```text
__Host-Http-skytrace-admin-session
```

若目标浏览器对较新的 `__Host-Http-` 前缀支持不完整，可采用广泛支持的 `__Host-skytrace-admin-session`，但仍必须显式设置 HttpOnly。

最低属性：

```http
Set-Cookie: __Host-Http-skytrace-admin-session=<opaque>;
  Path=/;
  Secure;
  HttpOnly;
  SameSite=Strict
```

规则：

- 不设置 `Domain`。
- production 只通过 HTTPS。
- Cookie value 是高熵随机 session ID，不是 JWT。
- 登录、权限提升、MFA step-up 后轮换 session ID，阻止 fixation。
- 注销使用相同 Path/Secure/SameSite 属性清除。
- 若业务要求从 Keycloak 跨站回调后立即带 Cookie，需验证 Strict 对实际重定向流程的影响；callback 建立新 Cookie通常可行。如必须 Lax，应记录理由和补偿性 CSRF 控制。

[RFC 10017](https://www.rfc-editor.org/rfc/rfc10017.html) 对 BFF Cookie 明确要求 Secure、HttpOnly，并建议 SameSite=Strict、Path=/、不设置 Domain。

### 3.2 有效期

建议评审起点：

| 项目 | 起始建议 | 说明 |
| --- | --- | --- |
| Admin access token | 5–10 分钟 | 缩短被盗 token 窗口 |
| BFF idle timeout | 30 分钟 | 有操作才滑动，last-seen 写入需节流 |
| BFF absolute timeout | 8 小时 | 不因持续操作无限延长 |
| recent MFA | 5 分钟 | 用于角色、绑定、日志清除等高风险动作 |
| OIDC state/nonce/PKCE transaction | 5 分钟，一次性 | callback 后立即删除 |
| revoked session metadata | 按审计政策 | token ciphertext 尽快清理 |

最终值由安全、产品和值班团队签字，不能只从代码默认值推导。

### 3.3 CSRF

Cookie 自动随请求发送，因此 BFF 必须实现 CSRF 防护。SameSite 不是唯一防线，尤其 `admin.example.com` 与被接管的其他 `*.example.com` 属于 same-site。

推荐组合：

1. Admin SPA 与 BFF 同 origin。
2. 所有状态变更只允许 JSON/明确 Content-Type。
3. 要求自定义 `X-SkyTrace-CSRF` header。
4. header token 与当前 session 绑定并在登录/step-up 后轮换。
5. 校验 `Origin`；无 Origin 的浏览器状态变更按策略拒绝或严格校验 Referer。
6. CORS 使用精确 allowlist；同源部署时原则上不开放跨源 credentials。
7. GET/HEAD/OPTIONS 必须无副作用。

`admin-service/src/main.ts:13` 当前直接 `enableCors()`，目标 BFF 模式应改为同源关闭或精确 allowlist，不能延续宽松默认。

测试必须覆盖普通 form POST、text/plain、multipart、缺 header、错误 Origin、同 site 不同 origin、预检绕过和双重提交 token 重放。

## 4. Token 验证基线

### 4.1 必须验证

- JWT 三段结构和合理最大长度。
- 明确允许的算法；当前 Keycloak 目标可固定 RS256，未来算法迁移需版本化配置。
- `kid` 对应可信 JWKS key，JWK 的 `kty/use/alg` 合理。
- 签名。
- 精确 `iss`。
- `aud` 包含 `skytrace-admin-api`。
- `azp`/authorized party 在允许的管理 client 集合中；它是补充，不替代 audience。
- `exp` 必须存在且未过期。
- `nbf`、`iat` 合理，时钟容差保持很小。
- `sub` 是非空字符串。
- `resource_access.skytrace-admin-api.roles` 中的管理 portal role。
- callback ID token 的 nonce 和 client audience。
- step-up 时的 `auth_time` 和经验证的 `acr`/认证方法。

`typ`/`at+jwt` 是否强制取决于 Keycloak 实际 token profile；上线前通过真实 token contract 测试确定。不能在未配置 IdP 输出时直接假设 claim 存在，也不能因此放弃 ID/access token 分离测试。

### 4.2 JWKS

- 启动可预热 JWKS，但 Keycloak 短暂不可用不能让已有可验证 session 无条件全部失效。
- 已缓存、未超过安全 stale window 的签名 key 可按批准策略继续使用。
- 未知 `kid` 最多触发一次合并刷新，并设置冷却/负缓存，防止请求放大。
- JWKS 响应设置连接/读取超时、最大 body/key 数和类型过滤。
- 新集合验证成功后原子替换；保留轮换重叠 key 至 token 最大 TTL。
- 记录 refresh success/failure/unknown-kid 指标，不记录 token。

### 4.3 Fail closed

- production 缺 issuer、audience、client ID、secret、encryption key 或 auth mode 时启动失败。
- property 缺失不能进入 permit-all。当前 Gateway 与 Backend 的安全配置在 property 真缺失时使用 `matchIfMissing=true` 的 permit-all chain，应在相关安全修复中改为显式 local/test 才能关闭。
- Keycloak/JWKS 未知状态不能自动尝试 legacy verifier。
- 权限缓存故障时回源 DB 或拒绝，不能使用无限过期的旧权限。
- `ADMIN_AUTH_MODE=dual` 的 HS 与 RS 验证器按算法/issuer/入口显式分派，OIDC 失败后不尝试 legacy。

## 5. 浏览器安全基线

BFF 阻止 token 被 JavaScript直接读取，但 XSS 仍可在用户浏览器内调用 BFF，因此必须同时做：

- 严格 CSP，优先无 `unsafe-inline`；若组件库短期依赖 inline style，记录收紧路线。
- `object-src 'none'`、`base-uri 'self'`、`frame-ancestors 'none'`、受控 `connect-src`、`form-action` 只允许自身和必要 Keycloak endpoint。
- `X-Content-Type-Options: nosniff`。
- 防 framing、Referrer-Policy、Permissions-Policy。
- 第三方脚本最小化，依赖锁定和供应链扫描。
- 不使用 `dangerouslySetInnerHTML`；服务端菜单/path、跳转 URL 和富文本均做白名单。
- `authorizedFetch`/Axios 不把认证相关 header 发到跨 origin。
- callback 和错误页不把 code、state、错误详情写进 analytics/referrer。
- 所有认证响应 `Cache-Control: no-store`，代理/CDN 不缓存 session 相关响应。

## 6. 登录与 API 限流

建议分别限流：

| 入口 | Key | 策略 |
| --- | --- | --- |
| `/auth/oidc/login` | IP 前缀 + browser transaction | 防重定向放大，允许正常重试 |
| `/auth/oidc/callback` | state/session + IP 风险信号 | 单次 code、严格失败计数 |
| `/auth/session` | session/IP | 防匿名轮询滥用 |
| legacy `/auth/login` | username HMAC + IP | P0 过渡控制，避免账号枚举 |
| legacy `/auth/refresh` | token family/session fingerprint | 防重放风暴 |
| 高风险管理动作 | local user + session | 业务限速 + recent MFA |

错误文案避免区分“用户名不存在”和“密码错误”。限流状态需要外部共享存储才能在多实例一致；本地内存 limiter 只可作为补充。

## 7. Secret 与加密

### 必须由 Secret Manager/受控环境提供

- `skytrace-admin` confidential client credential。
- OIDC session token encryption key。
- CSRF/HMAC key。
- legacy `JWT_SECRET`、`JWT_REFRESH_SECRET`（仅迁移期）。
- Keycloak bootstrap/admin 和数据库凭据。

### 生命周期

- secret 有 owner、用途、创建日、轮换周期和最后验证日。
- 支持至少 current/previous 两个 key version 的受控解密窗口。
- client secret 轮换先让服务端双 secret 验证/切换，再撤旧；具体能力需按 Keycloak client authentication 方案确认。
- encryption key 轮换采用新写旧读并后台重加密，不能一次性使所有 session 无法解密。
- legacy secret 在旧验证流量归零后撤销；不得继续留作无期限后门。
- 日志、错误、heap dump、APM request capture 均不得采集 secret/token/Cookie。

## 8. 审计与隐私

### 记录

- 登录成功/失败、错误类别、issuer、client、subject 的受控标识。
- binding 创建、审批、撤销、冲突。
- local user 启用/禁用、角色与权限变化。
- session 创建、刷新失败、撤销、全端注销。
- MFA enrol/reset、recent authentication、break-glass 操作。
- Keycloak desired-state 变更、操作者、diff、审批和部署结果。

### 不记录

- password 与密码重置值。
- authorization code、state 明文、nonce、PKCE verifier。
- access/refresh/ID token。
- Cookie/session ID 原值。
- client secret、加密 key、CSRF secret。
- 包含上述字段的完整 request body/header。

当前 Admin operation log 会序列化请求体，总审计已将登录、建用户、改密秘密泄漏列为发布阻断项。迁移前必须完成递归字段脱敏和历史数据处置。

### 保留与访问

- 身份审计保留期由合规/安全确定。
- subject、IP 和 user agent 属于个人/安全数据，只向有职责人员开放。
- session fingerprint 使用服务端 HMAC/不可逆标识，只用于关联。
- “清空日志”本身需要更高权限、recent MFA、双人确认；不可删除独立安全审计汇聚中的记录。

## 9. 可观测性

### 指标

```text
admin_auth_login_total{result,reason,auth_source}
admin_auth_callback_total{result,reason}
admin_auth_session_total{state}
admin_auth_refresh_total{result,reason}
admin_authz_decision_total{result,permission}
admin_identity_binding_total{state}
admin_jwks_refresh_total{result}
admin_legacy_auth_request_total{endpoint,result}
admin_step_up_total{result,operation}
```

标签必须是有限集合，不能把 username、subject、session ID、request ID 作为 metrics label。

### 日志字段

- requestId/traceId。
- auth source。
- stable error code。
- local user id（按访问政策）。
- issuer/client 的规范化标识。
- subject/session 的 HMAC fingerprint。
- permission decision 和 endpoint template，不记录原始敏感 query/body。

### 告警

- 登录/callback 失败率突增。
- 未绑定身份尝试突增。
- wrong audience/issuer/algorithm。
- unknown kid/JWKS 持续失败。
- refresh replay/invalid_grant 异常。
- super admin 角色或 identity binding 变更。
- legacy auth 在停签后仍有调用。
- Keycloak 配置 drift。
- session 解密失败或 key version 未知。

## 10. Keycloak 高可用与恢复

生产最低要求：

- 使用 production mode 和受支持的外部数据库；当前 production overlay 已从 base `start-dev` 改为 `start` + MySQL，但仍需真实 HA 设计。
- 数据库定期备份，按计划实际恢复到隔离环境，不只检查备份文件存在。
- realm 配置导出与数据库备份使用同一 release checkpoint。
- 至少两个 Keycloak 实例时，验证 discovery、session cache、反向代理 sticky/集群要求和滚动升级。
- 健康/readiness 区分进程存活与可登录能力。
- NTP/时钟同步；资源服务 token 容差不能用来掩盖系统时钟漂移。
- DNS、TLS 证书、hostname/proxy headers 和外部 issuer 完全一致。
- 升级前阅读 Keycloak server 和 adapter 升级说明，在独立 release 演练；不要与 Admin 切换、签名 key 轮换同时进行。

## 11. Realm drift 检查

每次部署应核对：

- realm enabled、registration/reset/remember-me 策略。
- clients 类型与 flow。
- redirect/post-logout URIs 和 web origins。
- scopes、audience mapper、client roles、role assignment。
- admin authentication flow/MFA required actions。
- token/session lifetimes。
- direct grant、implicit、offline access 和 full scope 状态。
- 是否存在测试用户、共享密码、过宽 service account role。
- active signing keys 和 rotation overlap。

drift 检查失败应阻止生产变更，不能自动用不完整 JSON 覆盖实际 realm。

## 12. 事件处置 Runbook

### 12.1 怀疑浏览器/session 泄漏

1. 本地禁用或撤销指定 Admin session/全部 sessions。
2. 撤销对应 Keycloak session/refresh grant。
3. 若身份可能失陷，禁用用户和重置凭据/MFA。
4. 查询同一 subject/session fingerprint 的操作日志和异常 IP。
5. 保全证据，不把 token 复制进 ticket/chat。
6. 修复 XSS/依赖/终端根因后再恢复。

### 12.2 client secret 泄漏

1. 将 `skytrace-admin` client 视为失陷，启动 client credential 轮换。
2. 检查异常 code exchange/refresh。
3. 视风险撤销全部 Admin sessions。
4. 清查 secret 来源：CI log、环境快照、镜像、APM、人员访问。
5. 轮换完成后验证旧 credential 失败。

### 12.3 Keycloak 不可用

1. 已有本地 Admin session 可否继续，取决于所需 token/JWKS 是否仍安全可用；未知身份一律不能新登录。
2. 本地权限检查继续 fail closed。
3. 向用户显示认证服务不可用，而不是本地密码回退。
4. 检查 Keycloak、数据库、DNS、TLS、proxy、issuer、JWKS 和时间。
5. 恢复后验证新登录、refresh、logout和 key 状态。

### 12.4 签名 key 异常/泄漏

1. 冻结认证变更，确认影响 kid 和签发时间。
2. 在 Keycloak 生成/启用新 key，并保持受控 overlap 以避免误伤或按事件级别立即撤旧。
3. 强制 JWKS 刷新，撤销受影响 sessions/tokens。
4. 检查所有资源服务是否拒绝旧/伪造 token。
5. 记录事件时间线和证据。

### 12.5 身份绑定错误

1. 立即撤销 binding 与相关 sessions，本地 user status 可临时禁用。
2. 保留错误 binding 历史，不硬删除证据。
3. 检查错误主体执行过的所有操作。
4. 重新走双人审批绑定。
5. 复盘是否发生 username/email 自动匹配、清单抄写或审批绕过。

## 13. Break-glass

首选恢复路径不是在应用里永久保留第二套公开登录，而是：

- Keycloak 基础设施的受控恢复账号。
- 两枚以上离线保存的硬件密钥/恢复介质，由不同人员保管。
- 数据库、realm 和 secret 可恢复 runbook。
- 通过 VPN/bastion/mTLS 访问 Keycloak 运维面。

如果业务连续性政策强制要求应用级 break-glass，必须同时满足：

- 默认完全关闭，无公开 UI 和普通路由发现。
- 只从独立管理网络/VPN + mTLS 访问。
- 两人批准后短时开启，自动超时关闭。
- 凭据为单次、高熵、Vault 托管，不在 seed、Git、Compose 默认值中。
- 权限仅覆盖恢复所需最小动作，不自动等同 `super_admin` 全权限。
- 每次尝试实时外部告警并写入不可由应用管理员清除的审计系统。
- 每季度演练启用、使用、关闭和凭据销毁。

应急通道不是 legacy 登录永久保留的理由。

## 14. 管理员生命周期 Runbook

### 入职/授权

1. 企业/Keycloak 建立实名身份。
2. 完成 MFA enrolment。
3. 授予 `ADMIN_PORTAL_ACCESS`。
4. 本地创建授权档案。
5. 双人审核 `(iss, sub)` binding。
6. 按最小权限分配本地角色。
7. 首次登录验证权限快照。

### 调岗

1. 先调整本地细粒度角色并失效 permission cache。
2. 高风险降权可撤销当前 sessions。
3. 是否保留 portal access 由职责决定。
4. 对比变更前后 permission set 并审计。

### 离职

1. IdP/Keycloak 禁用。
2. 本地 status 禁用或 binding 撤销，立即 deny。
3. 撤销所有 BFF 和 Keycloak sessions。
4. 移交最后一个 super admin 等关键职责。
5. 保留审计与身份 tombstone，不立即物理删除。

### 定期复核

- 至少季度复核 active admin、portal role、local roles、MFA 和最近登录。
- 长期未用账号自动进入复核/禁用候选。
- super admin、Keycloak realm admin 和 automation accounts 单独列报。

## 15. 安全上线门槛

- 生产 realm 无开发/默认/共享账号。
- Admin client confidential、PKCE、MFA、exact redirect、full scope off。
- 独立 Admin audience，业务 token 测试为拒绝。
- Cookie/CSRF/session fixation 负向测试通过。
- 浏览器任何 storage、响应、日志中无 JWT/refresh token。
- issuer/audience/alg/nonce/time/subject/role 全部有真实 token 测试。
- 身份 binding 100% 有审批证据，零自动 username/email 绑定。
- 本地 status 与 RBAC 拒绝即时生效。
- Keycloak/DB 备份和恢复实际演练通过。
- key rotation、client secret rotation、session revocation 演练通过。
- P0 日志秘密、默认 seed 和 RBAC 提权问题已关闭。
- 监控、告警、值班 runbook 和 break-glass 控制已验收。
