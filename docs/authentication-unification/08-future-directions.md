# 08. 后续演进方向

实施状态：**方向性规划；当前只完成文档与注释，未实施首次认证切换或本章演进项**

## 1. 演进原则

`v1.3.0` 的目标是先把最危险的重复身份体系收敛为：Keycloak 统一身份、Admin 独立 BFF、本地细粒度 RBAC。稳定之后再逐步解决业务 API audience、机器身份、自动 provisioning 和更强认证。

后续演进按三个层次排序：

1. 近期必做：消除仍然存在的 token 边界混淆，完善注销和账号治理。
2. 中期增强：自动化人员生命周期、step-up、统一验证组件和访问复核。
3. 条件触发：独立 realm/实例、sender-constrained token、外部策略引擎等高复杂度能力。

不要在 Admin 首次切换中同时完成全部方向。

## 2. 近期方向 A：业务 client 与 resource audience 解耦

### 当前问题

当前：

```text
browser client id = skytrace-web
resource audience  = skytrace-web
role source        = realm roles + resource_access[skytrace-web]
```

`gateway-java`、`backend-java` 和 `backend-node` 都以 `skytrace-web` 为 audience。这会把“谁请求 token”和“token 给哪个资源”混为一个名字，也增加 ID token/access token confusion 和未来 client 横向复用的风险。

### 目标

```text
browser client       = skytrace-web
business resource    = skytrace-business-api
machine client       = skytrace-service
business client roles= resource_access.skytrace-business-api.roles
```

### 迁移顺序

1. 创建 `skytrace-business-api` resource/audience 和 client roles。
2. producer token 同时包含旧 `skytrace-web` 与新 audience，或资源服务先支持明确的双契约。
3. Gateway/Java/Node 使用成对规则识别旧/新 token；不能“任一 audience + 任一 role”交叉组合。
4. 对浏览器、service account、WebSocket 和 Node→Java 传播跑完整矩阵。
5. 新 audience 命中达到 100%，超过 token 最大 TTL。
6. 移除旧 audience 和旧 role 读取。

该迁移可以从 `v1.4.0` 做 additive 双读，最终旧契约删除按兼容政策进入 `v2.0.0`。

## 3. 近期方向 B：realm roles 收敛为 resource client roles

当前 `ADMIN/OPERATOR/VIEWER` 是 realm roles，任何 client 若 scope/mapper 过宽都可能带上它们。目标：

- 业务角色位于 `skytrace-business-api` client role namespace。
- 管理准入位于 `skytrace-admin`/Admin resource namespace。
- Keycloak realm roles 只用于极少数真正跨应用的组织级组合角色，且 composite 关系可审计。
- `fullScopeAllowed=false`，每个 client 显式绑定 scope/role。

角色迁移不能只改 Keycloak；前端 `frontend/src/auth/keycloak.ts` 当前只读 `realm_access.roles`，Gateway/Java/Node converter 也会合并 realm roles。必须先兼容 client roles，再切 Keycloak，再删除 realm-role 依赖。

## 4. 近期方向 C：Back-channel logout 与即时 session 控制

短 JWT 的注销通常仍有一个 access token 有效窗口。Admin BFF 已能通过本地 session 即时 deny，但还应完善：

- Keycloak back-channel logout 到 Admin Service。
- 以 `sid` 索引本地 OIDC sessions，收到合法 logout token 后批量撤销。
- Keycloak 用户禁用、MFA reset、密码变化和高风险事件触发 session revoke。
- Admin local status/role 变化触发本地 session/permission version失效。
- 业务 WebSocket 当前按 token `exp` 断开；高风险需求下可接入 session/revoke event 主动断开。

Back-channel logout token 自身也需要 issuer、audience、事件 claim、签名、重放保护和审计测试，不能把 endpoint 做成任意 session id 注销接口。

## 5. 近期方向 D：管理员访问定期复核

建立季度或更短周期的自动报表：

- Keycloak `ADMIN_PORTAL_ACCESS` holders。
- Admin local active users/bindings/roles。
- `super_admin`、Keycloak realm admins、automation clients。
- MFA enrolment/最后验证时间。
- last login、长期未使用账号、无 owner 账号。
- Keycloak 与 PostgreSQL 中不一致的 disabled 状态。

复核结果必须能产生：继续授权、降权、禁用、解除 binding、owner 修正等可追踪任务。只导出 CSV 不跟踪处置不算完成。

## 6. 中期方向 A：自动 Provisioning 与 Reconciliation

首切建议采用人工预建 Keycloak 身份和显式 binding。规模增长后可加入：

### 6.1 最小权限 provisioner

- 独立 confidential client，不复用 `skytrace-service` 或 Admin browser client。
- 只授予目标 realm/用户所需的查询、创建、禁用和 required action 权限。
- 不授予 `realm-admin`。
- Secret/KMS、审计、限速和审批独立治理。

### 6.2 Outbox 工作流

Keycloak 与 PostgreSQL 不共享事务，采用：

```text
Admin 请求
  -> PostgreSQL 写 provisioning job/outbox
  -> worker 幂等调用 Keycloak
  -> 保存 subject/binding
  -> 失败重试/进入人工队列
```

创建、禁用、重命名和删除都需要幂等 key、状态机、补偿和对账。不能在 HTTP transaction 中先创建 Keycloak 用户、数据库失败后直接硬删除来“补偿”。

### 6.3 Reconciliation

定时检查：

- Keycloak active + 本地无 binding。
- binding active + Keycloak subject 不存在/disabled。
- local disabled + Keycloak 仍有管理 portal role/session。
- 重复 email/username 只作为人工线索，不自动修复。

## 7. 中期方向 B：统一 Step-up Policy

建议为高风险操作定义统一 policy，而不是每个 controller 临时判断：

| 操作 | 目标认证强度 |
| --- | --- |
| 查看普通列表 | 当前有效 MFA session |
| 创建/禁用管理员 | recent MFA，例如 5 分钟 |
| 分配 `super_admin` | recent phishing-resistant MFA + 双人审批 |
| 重置他人 MFA/身份 binding | recent MFA + 独立审批 |
| 清理操作日志 | recent MFA + 明确保留策略 |
| 启用 break-glass | 两人批准 + mTLS/VPN + 外部告警 |

服务端通过已验证的 `auth_time`、`acr` 和 session step-up 状态决策。实施前必须确认 Keycloak 实际 flow/mapper 输出，不能仅检查前端传来的布尔值。

## 8. 中期方向 C：Framework-neutral 认证契约包

当前 Gateway/Backend Java、backend-node、Admin Service 分别实现 JWT 校验/角色转换。可建立小型、框架无关的契约资产：

- JSON token fixtures：valid/wrong issuer/wrong audience/ID token/key rotation。
- claim schema 和稳定错误码。
- audience/client-role 成对规则。
- JWKS/issuer 配置命名标准。
- conformance tests。

不建议直接发布一个同时耦合 Spring、Nest 10、Nest 11 和浏览器的“大一统认证 SDK”。共享纯契约、fixture 和无框架 verifier 核心即可，framework adapter 各自维护。

## 9. 中期方向 D：机器身份独立治理

人员 token 和机器 token 应清晰区分：

- 机器使用 client credentials/workload identity，无人类 username/MFA。
- `sub`/client identity 与人类 user namespace 区分。
- audience 只包含要调用的资源。
- scope/client role 最小化，`fullScopeAllowed=false`。
- 高风险自动化用 mTLS/private-key JWT，而非长期共享 client secret（按 Keycloak 支持与运维能力评估）。
- secret/证书有自动轮换、owner、到期告警和调用审计。
- automation 不能借用 `super_admin` 本地角色；建立专用 service principal/permission model。

若未来允许机器调用 Admin API，优先新增独立 endpoint scopes 和 resource owner 模型，不复用浏览器 Cookie session。

## 10. 中期方向 E：主业务前端是否采用 BFF

业务前端当前 `keycloak-js` + PKCE + 内存 token 已是合理公共 SPA 模式，但 JavaScript 能访问 access/refresh token。是否迁到业务 BFF 需要单独威胁/成本评估：

### 迁移触发条件

- 业务前端处理更高敏感度证据或管理能力。
- XSS/第三方脚本风险无法接受。
- 已有稳定横向扩展 BFF/session 基础设施可复用。
- 团队能承担 Cookie/CSRF、WebSocket/SSE 代理和会话状态。

### 不应机械迁移的原因

- 业务前端有 Socket.IO、SSE、上传和多个后端，BFF 代理范围更大。
- Cookie 模式新增 CSRF 和 session state 运维。
- Admin BFF 的成功经验应先稳定，再决定是否复制。

[RFC 10017](https://www.rfc-editor.org/rfc/rfc10017.html) 将 BFF列为三种浏览器 OAuth架构中安全性最高的模式，但也明确存在复杂度权衡。决策要基于 SkyTrace 业务威胁模型，而不是为了形式统一。

## 11. 条件方向 A：独立 Admin realm 或 Keycloak 实例

### 同 realm 的当前推荐

首切使用同一 `skytrace` realm、独立 client/audience/flow，优点：

- 同一人员身份与 SSO。
- 迁移和运维成本较低。
- `(iss, sub)` 一致。
- 可以通过 client flow 强制管理 MFA。

### 独立 realm 的触发条件

- 组织要求管理身份与业务身份独立生命周期。
- 不允许业务 SSO 影响管理登录。
- 不同管理员团队和审计边界。
- realm 级密码/MFA/session policy 无法按 client 满足。

### 独立实例的触发条件

- 要求真正的故障域、网络和运维权限隔离。
- 管理 IdP compromise 不能与业务 IdP 共因。
- 法规/客户合同明确要求物理或平台级隔离。

注意：同一个 Keycloak 实例中的不同 realm 是逻辑隔离，不是完整故障域隔离。迁移 issuer 后 `(iss, sub)` 会变化，需要新的 binding 和双 issuer 过渡，不能轻率实施。

## 12. 条件方向 B：Sender-constrained Tokens

[RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html) 建议在适用时用 mTLS 或 DPoP降低 token replay。候选场景：

- Admin automation 使用 mTLS/private-key client authentication。
- 高价值外部 API 使用 DPoP/mTLS access token。
- 服务间调用使用 workload identity 和短期证书。

浏览器 XSS 控制整个应用上下文时，DPoP 不能解决所有 client hijacking；BFF、CSP、最小 audience 和短 TTL 仍是基础。只有 Keycloak、Gateway、所有资源服务和客户端都能完整支持并测试时才引入。

## 13. 条件方向 C：外部策略引擎与 ABAC

当前 Admin permission code + PostgreSQL RBAC 对项目规模更直接。只有出现以下需求才评估 OPA/Cedar/Keycloak Authorization Services 等策略层：

- 多租户/组织/项目/设备的数据范围授权。
- 同一动作依赖资源属性、时间、网络、风险等级。
- 多个服务需要共享同一动态政策。
- 需要策略版本、模拟和集中审计。

引入前必须解决：

- policy decision latency/availability。
- 默认拒绝和缓存一致性。
- policy/data ownership。
- rollback 和历史决策可解释性。
- 与现有 Admin local RBAC 的单一事实来源。

不要同时让 Keycloak role、Admin DB role 和外部引擎都能单独放行；授权组合必须明确为 deny-overrides 或完整 policy。

## 14. 条件方向 D：企业 IdP/SCIM

如果接入企业 OIDC/SAML/LDAP：

- Keycloak 仍作为 SkyTrace broker/统一 issuer，资源服务不直接信任多个外部 issuer，降低 mix-up 和配置复杂度。
- 外部 identity 的稳定键仍经 Keycloak `sub` 映射；同时保留 upstream identity 审计字段但不直接授权。
- 管理 portal role 不应按任意 IdP group 自动宽泛映射。
- SCIM provisioning 与登录 authentication 分开治理；SCIM 删除先本地 deny 和 session revoke。
- IdP link/unlink 是高风险身份绑定操作，需要审计和保护。

## 15. 条件方向 E：Passkeys 与抗钓鱼认证

在 Admin MFA稳定后，逐步提高认证强度：

1. 先要求所有管理员至少 TOTP/WebAuthn 第二因素。
2. 为 super admin 和 Keycloak realm admin 优先要求 WebAuthn/passkey。
3. 验证 user verification、可接受 authenticator、设备丢失和恢复码策略。
4. MFA reset 采用独立身份核验和双人审批。
5. 根据实际客户端支持再考虑 passwordless，不牺牲可恢复性。

Keycloak 官方 [Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/index.html) 提供 WebAuthn、OTP 和恢复码 flow 配置；具体认证强度仍需组织政策定义。

## 16. 未来版本排序

| 版本/里程碑 | 建议方向 | 不应混入 |
| --- | --- | --- |
| `v1.3.1+` | Admin BFF 稳定性、back-channel logout、观测修复 | 业务 audience 大迁移 |
| `v1.4.0` | provisioning/outbox、step-up、access review、业务 audience双读 | 删除旧对外契约 |
| `v1.5.0` 候选 | framework-neutral conformance、机器身份治理、主前端 BFF 调研 | 未评审的独立 realm |
| `v2.0.0` | legacy auth contract 删除、旧业务 audience/role contract收口 | 无兼容层的一次性全域重写 |
| 条件专项 | 独立 realm/instance、DPoP/mTLS、ABAC、企业 IdP | 与紧急安全补丁同发 |

版本只描述兼容边界，不代表必须按固定日历发版。

## 17. 演进决策检查表

每个后续方向开始前回答：

- 解决的具体威胁/业务需求是什么？
- 当前控制为何不足？
- 新增的状态、secret、故障域和运维成本是什么？
- 哪个组件是身份/权限真相来源？
- token 的 client、audience、role、scope 和 subject 分别是什么？
- 旧客户端如何双读/迁移/退出？
- 负向安全测试与回滚如何完成？
- 是否需要 minor/major 版本？
- 谁长期拥有运行和事故处理？

无法具体回答时，不应仅因“更统一”或“更先进”引入新认证组件。

## 18. 长期目标状态

最终理想状态可以概括为：

```text
人类身份
  -> Keycloak/企业 IdP，强 MFA，统一生命周期
  -> client/audience 隔离的短期凭据
  -> 每个资源服务严格验证
  -> 领域内动态授权和即时 deny

浏览器管理面
  -> Admin BFF，token 不暴露给 JavaScript
  -> HttpOnly session + CSRF + step-up

机器身份
  -> 独立 workload client/证书
  -> 最小 audience/scope，无人类共享账号

治理
  -> desired state、审计、访问复核、备份恢复、轮换和演练
```

达到这一状态的关键不是“所有系统用同一枚 token”，而是所有身份遵守同一治理体系，同时每个安全域只接受为自己签发、满足自己授权规则的凭据。
