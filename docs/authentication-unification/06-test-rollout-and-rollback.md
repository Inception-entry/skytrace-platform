# 06. 测试、灰度、切换与回滚

实施状态：**测试与发布计划；未新增测试场景、未执行认证切换，现有测试只补充了注释**

## 1. 质量目标

认证迁移的验收标准不是“能跳转 Keycloak 并看到首页”，而是同时证明：

- 正确身份能够登录。
- 错误 token、错误 client 和错误 audience 均被拒绝。
- Keycloak 身份稳定映射到原本的本地权限。
- 禁用、撤权、注销、MFA 和 session 过期按预期生效。
- 并发刷新、多个标签、服务故障和 key rotation 不产生越权或死锁。
- 双轨发布的前后版本组合可控。
- 回滚不会恢复已泄漏或已停用的危险凭据。

## 2. 当前测试基线与缺口

当前已有的正向基础：

- Gateway、Backend Java 有 Spring Security integration test 和 role converter test。
- backend-node 有 Keycloak JWT service/guard 基础测试。
- admin-service 有本地 login、refresh rotation、logout、改密撤销等 service tests。
- 业务 E2E helper 能完成一次 Keycloak 页面登录。

当前关键缺口：

- Java 集成测试多使用 mock authority，未真实覆盖 issuer/audience/ID-token confusion/错误签名。
- Admin frontend 尚无行为测试框架，当前 refresh deadlock、logout 竞态和多标签行为未被自动验证。
- Admin service 测试只覆盖本地 HS JWT，没有真实 Keycloak/JWKS、identity binding 或 BFF session。
- 现有 realm 验收脚本会临时开启 password/direct grant；新验收不能靠改变受测安全配置取得 token。
- 没有真实 MFA、callback state/nonce/PKCE、CSRF、Cookie、back-channel logout、key rotation 和回滚演练。

## 3. 测试环境分层

| 环境 | 用途 | 身份数据 | 是否允许假 IdP |
| --- | --- | --- | --- |
| 单元测试 | 纯函数、状态机、claim 解析、权限决策 | 固定 fixture | 可以，但需验证边界 |
| 组件/集成 | Admin Service + PostgreSQL + Keycloak/JWKS | 测试 realm/用户 | JWT 边界必须有真实或受控签名 key |
| 浏览器 E2E | SPA、Nginx、BFF、Keycloak 全流程 | 专用测试账号和 MFA fixture | 不允许 mock 掉登录主链 |
| fresh staging | 真实 HTTPS、真实域名、近生产拓扑 | 非生产实名测试账号 | 不允许 |
| production canary | 少量已批准管理员 | 真实账号 | 不允许 |

测试 realm 不能复用生产 realm。测试用户、密码和恢复码不能进入生产 desired state。

## 4. 单元测试矩阵

### 4.1 OIDC claim validator

| 场景 | 预期 |
| --- | --- |
| 正确 RS256、kid、issuer、audience、sub、role | 接受 |
| `alg=none` | 401 |
| HS token 尝试使用 RSA public key | 401 |
| 不允许的 RSA/EC 算法 | 401 |
| 缺 `kid`、未知 `kid` | 401；未知 kid 有界刷新 |
| 签名被篡改 | 401 |
| issuer 大小写/尾斜杠/路径不同 | 401，精确匹配 |
| audience string 正确 | 接受 |
| audience array 包含正确值 | 接受 |
| audience 为 `skytrace-web` | Admin 401 |
| 缺 audience | 401 |
| 正确 audience、错误 `azp` | 401/403，按契约固定 |
| ID token 冒充 access token | 401 |
| `exp` 缺失/过期/非数字 | 401 |
| `nbf` 超出小容差 | 401 |
| `iat` 明显在未来 | 401 |
| `sub` 缺失/空/非字符串 | 401 |
| realm `ADMIN` 但无 `skytrace-admin-api` resource client role | 403 |
| `skytrace-admin-api` 的 `ADMIN_PORTAL_ACCESS` 正确 | 进入 identity binding 检查 |

### 4.2 Identity binding

- 正确 `(iss, sub)` 返回唯一 local user。
- 相同 sub、不同 issuer 不互相绑定。
- username/email 改名不改变 local user。
- 未绑定返回 `ADMIN_NOT_PROVISIONED`。
- revoked binding 返回稳定拒绝并撤销 sessions。
- local user disabled 即时拒绝。
- 唯一约束阻止同一主体绑定两人。
- 并发审批只能成功一次。
- 审批者不能批准自己；super admin 要求双人审批。
- unbind 不删除历史记录。

### 4.3 Admin session

- session ID 至少满足规定熵，数据库只保存 hash。
- Cookie 值不能反推出 DB hash 输入以外信息。
- idle/absolute expiry 任一到期都拒绝。
- revoked session 拒绝。
- login、step-up、权限提升后 session ID 轮换。
- `last_seen_at` 更新节流，不因并发回退时间。
- 同一 session 20 个并发 refresh 只调用 Keycloak 一次。
- refresh rotation 后旧 token 不再使用。
- session token 解密 key current/previous 正常，未知版本拒绝并告警。
- logout 幂等；重复调用均清 Cookie，不恢复 session。

### 4.4 CSRF 与 return path

- 所有 POST/PUT/PATCH/DELETE 缺 CSRF header 拒绝。
- header 与 session 不匹配拒绝。
- 错 Origin、`Origin: null`、同 site 不同 origin 按策略拒绝。
- form-urlencoded、text/plain、multipart 尝试不能绕过。
- GET 不产生业务副作用。
- `return_to` 接受 `/admin/users?x=1#tab` 等合法站内路径。
- 拒绝绝对 URL、`//host`、反斜杠、控制字符、多次编码和用户名密码 URL。
- callback state、nonce、code 只能使用一次。

### 4.5 本地 RBAC

- `super_admin` 正常拥有全部 permission。
- 普通角色只获得菜单绑定的 permission codes。
- Keycloak role 不自动变成本地 `super_admin`。
- 非 super 不能分配/修改/删除 super role。
- 不能移除最后一个 active super admin，包括并发事务。
- role/menu/status 改变后权限缓存失效。
- session 中旧 permission version 不能继续越权。
- dashboard/upload 等只有 JWT guard 的入口补齐预期本地权限测试。

### 4.6 前端认证状态机

- booting 时不渲染受保护页面。
- anonymous 只触发一个 login 导航。
- not-provisioned/forbidden 不形成登录循环。
- session check 的网络故障进入 unavailable，可重试。
- 多个并发 401 只触发一个 recovery。
- recovery/navigation Promise 失败被消费并显示错误。
- logout 多击幂等。
- BroadcastChannel logout 同步所有标签。
- localStorage/sessionStorage/IndexedDB 序列化结果无 token。
- 首次新版本启动清除 `skytrace-admin-auth`。
- upload、普通 API、错误重试均走同一 client。

## 5. 集成测试矩阵

### 5.1 Admin Service + PostgreSQL

使用真实 migration 从空 PostgreSQL 建库：

- expand migration 前后的旧/新应用兼容。
- identity binding 唯一性和并发。
- session hash/encrypted fields 不包含 fixture token 明文。
- 本地 disabled/permission changes 下一请求生效。
- legacy/OIDC `/auth/me` snapshot 相同。
- legacy refresh rows 在停签后只减不增。
- session/transaction 清理任务只删除目标范围。

### 5.2 Admin Service + Keycloak

必须通过 Authorization Code flow 获取 token，不能临时打开 direct grant：

- confidential client authentication。
- PKCE S256。
- state/nonce。
- exact redirect URI。
- required MFA/required action。
- client role 和 audience mapper。
- refresh rotation/expiry。
- end-session/revocation。
- Keycloak 用户禁用、role 撤销、session revoke。
- signing key rotation 和 overlapping keys。
- discovery issuer 与服务配置不一致。

### 5.3 业务资源隔离

同一组真实 token 调用所有入口：

| 身份/token | Gateway `/api/**` | Java direct | Node direct | Admin BFF/API |
| --- | --- | --- | --- | --- |
| 业务 VIEWER | 读允许 | 按业务策略 | 按业务策略 | 拒绝 |
| 业务 ADMIN | 按业务策略 | 按业务策略 | 按业务策略 | 拒绝 |
| Admin portal user | 默认拒绝 | 默认拒绝 | 默认拒绝 | 按本地 RBAC |
| Admin automation | 拒绝 | 拒绝 | 拒绝 | 只允许明确 scope |
| legacy Admin token | 拒绝 | 拒绝 | 拒绝 | dual 期接受、切断后拒绝 |
| ID token | 拒绝 | 拒绝 | 拒绝 | 拒绝 |

### 5.4 多实例

- 两个 Admin Service 实例共享 session store。
- 请求轮询到不同实例仍保持登录。
- 并发 refresh 只发生一次。
- 一个实例撤销 session，另一个立即/在批准窗口内拒绝。
- encryption key rotation 滚动发布时新旧实例互操作。
- 权限缓存失效跨实例传播。

## 6. 浏览器 E2E 场景

### 登录与深链

1. 直接访问 `/admin/users?page=2`。
2. 未登录检查 session。
3. 导航 Keycloak，完成 MFA。
4. callback 后 URL 不残留 code/state。
5. 返回原深链，且只能是站内路径。

### SSO

- 已登录业务前台后访问 Admin：仍满足 Admin 专用 MFA/flow。
- Admin login 不自动给业务 API 权限。
- 切换账号使用明确 prompt，不复用错误用户。
- 全局 logout 是否同时影响业务前台，与产品文案一致。

### 权限

- 有 portal access 无 local binding：明确 403。
- 有 binding 但 local disabled：明确 403。
- 有 binding 无 `user:list`：菜单/页面隐藏且 API 403。
- 给角色新增权限后按批准时延出现。
- 撤销权限后现有页面下一次操作被拒绝。
- super admin 保护和 recent MFA 页面完整。

### Session

- 页面刷新仍通过 Cookie 恢复。
- 两个标签同时操作和退出。
- idle、absolute、Keycloak refresh expiry。
- 浏览器睡眠/唤醒和系统时间变化。
- Cookie 删除/损坏。
- 后退缓存不能显示可继续操作的敏感页面；响应 no-store。

### 故障

- Keycloak login endpoint 5xx/timeout。
- token endpoint 5xx/timeout。
- JWKS unknown kid/timeout。
- Admin PostgreSQL 不可用。
- session encryption key 缺失。
- callback 重复加载。
- 网络在“本地 session 已撤销、Keycloak logout 未完成”处中断。

### 浏览器安全

- Cookie 为 Secure、HttpOnly、Path=/、无 Domain、目标 SameSite。
- `document.cookie` 读不到 session。
- storage 中无 JWT。
- CSP、frame-ancestors、nosniff 等 header 生效。
- CSRF 跨站表单和 fetch 攻击失败。
- 恶意 return path/open redirect 失败。
- 认证响应不被 browser/proxy cache。

## 7. 安全测试与攻击用例

- JWT algorithm confusion、key confusion、`kid` 注入/超长值。
- oversized token/JWKS、JWKS key flood。
- issuer mix-up、callback mix-up、state swap、nonce replay、code replay。
- ID token 当 access token。
- audience/client role 混淆。
- 业务 token 打管理 API、Admin token 打业务 API。
- username/email takeover 和 subject collision。
- session fixation、session ID 猜测、Cookie tossing/subdomain fixation。
- CSRF、login CSRF、logout CSRF。
- refresh replay 和并发 rotation。
- 注销后 access window 与本地 deny。
- operation log/token/secret 泄漏扫描。
- 依赖已知漏洞、CSP 绕过和供应链脚本。
- rate limit 绕过：IPv4/IPv6、伪造 X-Forwarded-For、多实例。

发布前需由独立评审者复核；静态单元测试不能替代渗透/威胁验证。

## 8. 性能与容量测试

### 目标操作

- `/auth/session` p50/p95/p99。
- local RBAC 查询与 cache hit/miss。
- session last-seen 节流。
- 100 个同 session 并发请求时 refresh single-flight。
- Keycloak 新登录/refresh 吞吐。
- session 清理任务和索引。

### 故障下容量

- Keycloak 30 秒不可用时，不形成无限重试或重定向风暴。
- unknown kid 攻击不把每个请求放大成 JWKS 调用。
- PostgreSQL 慢查询时 session pool 不耗尽。
- 大量过期 session 清理分批执行。
- metrics label 不因 subject/session 导致高基数。

性能目标应根据实际管理员并发设定；文档不虚构 TPS。必须以预期峰值乘安全系数压测并记录资源占用。

## 9. 可观测验收

上线前准备 dashboard：

- login/callback 成功率和延迟。
- 401/403/503 按稳定 reason code。
- not-provisioned、disabled、wrong audience。
- active/revoked/expired session 数。
- Keycloak refresh 成功率、invalid_grant、JWKS refresh。
- local permission deny。
- legacy endpoint/validator 命中量。
- Admin Service/Keycloak/PostgreSQL 健康和连接池。

每个图表须有：owner、正常基线、告警阈值、观察窗口和 runbook 链接。禁止用 username/sub 作为指标标签。

## 10. Fresh staging 门禁

必须从新数据卷/新测试环境验证，不能依赖手工修过的旧 realm：

1. 以 desired state 创建 Keycloak realm/client/flow。
2. 从空 PostgreSQL 执行 Prisma migration。
3. 使用真实 staging HTTPS Admin origin。
4. issuer discovery、callback、CORS/CSRF/Cookie 验证。
5. 建立两个 super admin 和至少一个受限管理员。
6. 跑完整浏览器/接口/负向 token矩阵。
7. 模拟 Keycloak、DB、网络和 key rotation。
8. 演练 dual -> oidc-bff -> dual 回滚。
9. 导出审计、metrics、realm diff 和测试报告。

任何需要登录 Keycloak 控制台临时手改才能通过的步骤都算验收失败，必须回到 desired-state/migration 修正。

## 11. 灰度计划

### 11.1 灰度组

按风险递增：

1. 开发/安全团队的非唯一 super admin。
2. 内部运维管理员。
3. 只读/低权限管理员。
4. 其余管理员。
5. 最后才是唯一职责或值班关键账号；切换前必须已有替代人。

### 11.2 每组检查

- Keycloak MFA enrolment。
- binding 与 local role snapshot。
- 深链、`/me`、菜单和高频操作。
- logout/relogin/切换账号。
- legacy/OIDC auth source metrics。
- 错误工单和用户反馈。

### 11.3 放量条件

- 当前组至少覆盖一个 access token刷新周期。
- 无权限扩大或错误身份绑定。
- 401/403/callback/refresh 处于批准阈值。
- 至少两个 super admin 始终可用。
- 回滚开关和上一 frontend artifact 可用。

不建议用百分比随机灰度 OIDC 登录，因为同一管理员跨标签/跨实例被随机分到不同认证模式会产生混乱。使用明确用户/组织 allowlist 或独立 canary origin。

## 12. 正式切换 Runbook

### T-7 天至 T-1 天

- 冻结高风险认证/角色变更。
- 完成账号绑定与 MFA 清单。
- 备份 Admin PostgreSQL、Keycloak DB、realm desired/current export。
- 记录 legacy refresh rows、活跃用户和权限快照。
- 确认镜像 digest、配置、回滚 artifact、值班人员和通信模板。
- 完成 fresh staging、故障和回滚演练。

### T-0 部署

1. 部署 additive DB migration。
2. 应用 Keycloak migration并运行 drift/preflight。
3. 先部署 `ADMIN_AUTH_MODE=dual` Admin Service。
4. 冒烟旧 legacy frontend。
5. 启用 approved OIDC canary，验证 session/RBAC。
6. 部署 `oidc-bff` Admin frontend。
7. 清除旧浏览器 token storage；不要先清数据库 refresh rows。
8. 全量验证登录、`/me`、至少一个读写操作和注销。

### 稳定观察

- 观察完整 access token刷新周期。
- 统计 legacy login/refresh/validator 使用者。
- 处理未绑定/权限差异，不做 username 自动绑定。
- 达到门槛后停止 legacy 新签发和 refresh。
- 主动撤销旧 refresh rows，再等待最大 access TTL + 缓冲。
- 关闭 legacy validator。

### Contract

只在另一个发布窗口执行，见版本文档。不要与首次切换同日删除 password/session schema。

## 13. 回滚触发条件

任何一个条件达到批准阈值时停止放量；涉及越权/错绑时立即回滚：

- 身份 A 被映射为本地用户 B。
- Admin token 可访问未授权业务 API，或业务 token 可访问 Admin API。
- 本地 permission snapshot 意外扩大。
- 两个以上关键管理员无法登录，且非个别 MFA 操作问题。
- callback/login loop 持续出现。
- session 无法撤销或 logout 后仍可继续操作。
- refresh 并发造成大量 session 失效。
- session/token 明文进入 browser storage、响应、日志或数据库非加密字段。
- 401/403/503、Keycloak latency 或 DB error 达到预设阈值。

阈值数字需在 staging 基线后填写并审批，不能在没有流量数据时编造。

## 14. 分阶段回滚

### Phase 1（仅 Keycloak/data expand）

- 停止新 client 流量。
- 保留 additive tables/fields，不做 down migration。
- 回滚应用不受影响。
- 修复 desired state 后 forward-fix。

### Phase 2–4（dual + 前端切换）

1. Admin Service保持 `dual`，不能先降到 legacy-only。
2. 把前端运行时模式切回 legacy/上一 artifact。
3. 让浏览器重新登录 legacy；OIDC Cookie 可撤销并清除。
4. 保留 binding 和 OIDC session审计。
5. 确认旧 login/refresh 仍安全可用，且 P0 止血未被回滚。
6. 认证流量完全恢复后，再决定是否暂停 OIDC callback。

### Phase 5（已停止 legacy 签发）

- 若必须回滚，可短时重新启用签发，但先确认 legacy secrets 未泄漏、未撤销且用户 password 仍有效。
- 已删除 refresh rows 的用户需要重新密码登录，不能恢复旧 refresh token。
- 若用户已只维护 Keycloak 密码，需受控 legacy 密码重置；不能同步或导出 Keycloak 密码。

### Phase 6（legacy validator 已关闭）

- 仍在 contract 前，可回到 `dual`；先确认 validator binary/secret 在受控位置。
- 不因 Keycloak 故障自动打开 legacy，必须事故负责人批准并记录。

### Phase 7（旧字段/接口已删除）

- 不再属于快速应用回滚。
- 优先 forward-fix。
- 若灾难恢复，需恢复兼容数据库备份、旧 secret 和旧整套 artifact，影响窗口更大。
- 因此 contract 必须延迟到稳定发布后。

## 15. 回滚后验证

- 正确的旧/新入口可登录。
- 业务/Admin token仍互相隔离。
- P0 日志脱敏、默认账号移除、RBAC 保护仍在，不能随旧 artifact 一起回退。
- OIDC 新建的本地 binding 没有丢失或改变权限。
- 已撤销 session 不被恢复。
- Keycloak client/realm 不被破坏性删除。
- 所有回滚步骤、原因、操作者、时间和验证结果有审计。

## 16. 最终验收签字表

| 领域 | 必须签字角色 | 验收产物 |
| --- | --- | --- |
| 身份与 MFA | IAM/安全负责人 | realm/client/flow diff、MFA 测试 |
| Admin RBAC | 后台产品/系统 owner | 用户角色权限前后快照 |
| 数据迁移 | DBA/Admin Service owner | migration、备份恢复、binding 报告 |
| 前端体验 | Admin frontend owner | 浏览器 E2E、错误与无障碍状态 |
| 服务端安全 | Admin Service owner + 独立 reviewer | token/CSRF/session负向矩阵 |
| 发布运维 | SRE/值班负责人 | dashboard、告警、切换和回滚演练 |
| 版本契约 | Release owner | deprecation、release note、client 清单 |

只有这些证据完成，才能把本目录中的状态从“规划”改成“完成”。
