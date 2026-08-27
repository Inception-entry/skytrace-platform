# 00. SkyTrace 认证机制统一调整方案与阅读顺序

文档日期：2026-08-24  
适用基线：当前 `main` 工作树（产品模块版本仍为 `1.2.1`）  
实施状态：**认证调整仍仅处于方案阶段，尚未实施**  
变更边界：**本轮全仓仅补充中文注释和必要的文件末尾换行；未实施本方案，也未改运行行为、数据库迁移、依赖、版本号或发布产物**

> 本方案回答的不是“前台和后台能不能共用一枚 token”，而是如何做到：身份认证统一、管理面边界独立、细粒度授权不丢失、迁移期间可回滚。

> Keycloak 运行时仍导入 `deploy/keycloak/skytrace-realm.json`。阅读配置时可参考语义完全一致的 `deploy/keycloak/skytrace-realm.annotated.jsonc`；该 JSONC 仅用于人工阅读，不参与 Compose 或 Keycloak 导入。

## 结论

SkyTrace 应停止长期维护两套完整的用户名/密码与令牌系统，统一由 Keycloak 负责身份认证；同时必须保留业务平台与管理平台的安全隔离，不能让 `skytrace-web` 的普通业务 token 自动获得 Admin API 访问能力。

推荐最终形态：

- Keycloak 是唯一常规身份提供方，负责凭据、MFA、登录策略、SSO、会话和账号生命周期。
- 业务前台继续使用独立的 `skytrace-web` 公共客户端。
- 管理端新增独立的 `skytrace-admin` 机密客户端，采用 Authorization Code + PKCE，由 Admin Service 承担 BFF 职责。
- 管理浏览器只保存不可读的 `HttpOnly` 会话 Cookie，不再保存 access token 或 refresh token。
- Admin Service 以 Keycloak 的 `(iss, sub)` 绑定本地管理员；用户名和邮箱只用于展示，不能作为身份主键。
- Keycloak 的 `skytrace-admin-api` resource client role `ADMIN_PORTAL_ACCESS` 只负责“是否允许进入管理平台”；现有 PostgreSQL `sys_role`、`sys_menu`、`sys_user_role` 继续负责细粒度权限。
- `super_admin` 保留为本地受保护角色，不直接由通用 realm role 推导。
- 旧 `/auth/login`、`/auth/refresh` 和本地密码体系先双轨兼容，再停止签发，最后移除；不能一步硬切。

## 按顺序阅读

1. [01. 现状、问题与架构决策](01-current-state-and-decision.md)：两套认证现在分别怎么工作，哪些部分保留，哪些部分淘汰。
2. [02. 目标架构与信任边界](02-target-architecture.md)：Keycloak、业务前台、管理 BFF、Admin RBAC 之间的最终关系。
3. [03. 身份绑定、权限与数据模型](03-identity-rbac-and-data.md)：`iss + sub`、管理员绑定、角色来源、拟议 Schema 及数据迁移规则。
4. [04. API、前端与兼容迁移](04-api-and-frontend-migration.md)：登录、刷新、注销、`/me`、修改密码等接口如何逐步替换。
5. [05. 安全基线与运维要求](05-security-and-operations.md)：MFA、Cookie、CSRF、CORS、会话、审计、Keycloak 高可用和应急账号。
6. [06. 测试、灰度、切换与回滚](06-test-rollout-and-rollback.md)：必须补的正向/负向测试、可观测性、灰度门槛和回滚边界。
7. [07. 排序后的任务清单与版本建议](07-priority-backlog-and-versions.md)：按依赖和优先级排序的实施任务、验收条件和建议版本。
8. [08. 后续演进方向](08-future-directions.md)：管理 BFF 稳定之后，业务 audience、机器身份、细粒度授权和零信任方向。

## 方案状态词

为避免把“文档已有”误解成“功能已完成”，本目录统一使用以下状态：

| 状态 | 含义 |
| --- | --- |
| 当前事实 | 已在当前仓库中找到对应实现或配置 |
| 已决策 | 本方案推荐采用，但尚未开发 |
| 候选 | 需要在实施前通过 ADR、压测或安全评审确认 |
| 禁止 | 迁移过程中不应采用的做法 |
| 完成 | 只有代码、配置、数据、测试、发布和运维验收全部落地后才能标记 |

本目录目前只有“当前事实”“已决策”“候选”和“禁止”，没有任何“完成”项。

## 实施主线

```text
P0 先止血
  ↓
固定认证边界与账号清单
  ↓
配置独立 Admin OIDC 客户端和强制 MFA
  ↓
新增不可变身份绑定与双轨认证
  ↓
管理前端切换到 BFF Cookie 会话
  ↓
停止签发本地 JWT/refresh token
  ↓
观察、撤销旧会话、清理旧凭据
  ↓
移除遗留接口与字段
```

详细依赖和验收条件见 [07. 排序后的任务清单与版本建议](07-priority-backlog-and-versions.md)。

## 版本一句话建议

- 本次只有文档和注释：**不改版本号，也不发布产品版本**。
- 当前认证安全止血项保持兼容时：纳入 `v1.2.2`。
- Admin Keycloak/BFF 双轨接入、身份绑定和前端切换：建议 `v1.3.0`，先发 `v1.3.0-rc.1`。
- 删除已公开使用的旧登录/刷新契约：若无法证明仅供内部使用，安排到 `v2.0.0`；若契约明确是内部且已完成弃用窗口，可在后续 minor 完成。

## 与项目总审计的关系

本方案是 [项目只读审计](../project-audit/README.md) 中认证问题的专项落地设计。总审计仍是风险全集；本目录只覆盖身份认证、管理端会话、身份绑定、授权边界及相关发布流程。

## 外部规范依据

- [RFC 9700：OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700.html)
- [RFC 10017：OAuth 2.0 for Browser-Based Applications](https://www.rfc-editor.org/rfc/rfc10017.html)
- [Keycloak JavaScript Adapter 官方文档](https://www.keycloak.org/securing-apps/javascript-adapter)
- [Keycloak Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/index.html)

外部规范用于确定安全边界；具体字段、接口和阶段顺序仍以当前 SkyTrace 仓库结构为依据。
