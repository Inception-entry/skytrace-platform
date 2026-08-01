# 业务端权限、审计与管理概览

本文描述业务端（Vue、Gateway、Node BFF 与 Spring Boot）的权限和审计链路。
项目另有独立管理后台（`admin-frontend` 与 `admin-service`），默认地址为
`http://localhost:8889`。它使用独立的 NestJS JWT、PostgreSQL RBAC 和操作日志，
不与 Keycloak 业务用户共享会话或账号体系。

## 请求校验链

业务请求按照以下顺序校验同一个 Keycloak Bearer Token：

```text
Vue -> Nginx -> Spring Cloud Gateway -> Node BFF -> Java API
```

- Gateway 负责入口 JWT 校验、角色路由、限流、请求 ID 和访问日志。
- Node BFF 再次验证 JWT，并对接口方法执行角色校验。
- Java API 再次验证 JWT，避免通过本机映射端口绕过 Gateway/BFF。
- Node 只向 Java 转发已经验证过的 Bearer Token、请求 ID 和客户端 IP。
- Gateway 会删除客户端伪造的身份请求头，再根据已验证的 JWT 重建身份头。

Gateway 和 Java 的鉴权默认启用。只有测试或明确的本地调试场景才应通过
`GATEWAY_SECURITY_ENABLED=false` 或 `APP_SECURITY_ENABLED=false` 关闭。

## 角色矩阵

| 能力 | ADMIN | OPERATOR | VIEWER |
| --- | --- | --- | --- |
| 查看任务、告警、知识文档 | 是 | 是 | 是 |
| 创建、编辑和执行巡检任务 | 是 | 是 | 否 |
| 发起 AI 分析与聊天 | 是 | 是 | 否 |
| 搜索知识库 | 是 | 是 | 是 |
| 上传、删除知识文档 | 是 | 否 | 否 |
| 查看管理概况和审计记录 | 是 | 否 | 否 |

前端路由和按钮会按角色显示，但它们只负责用户体验。真正的安全边界位于
Gateway、Node BFF 和 Java API。

## 审计记录

Java API 会把关键写操作写入 MySQL 的 `audit_log` 表，包括：

- 操作人 ID、用户名和当时拥有的业务角色；
- 操作类型、资源类型和资源 ID；
- HTTP 方法、路径、状态码和成功/失败结果；
- 请求 ID、客户端 IP、执行耗时和异常类型；
- 审计记录创建时间。

为了避免泄露敏感数据，审计记录不保存 Bearer Token、Cookie、AI Prompt、
请求正文、上传文件内容或完整响应。Gateway 还会记录所有请求的结构化访问
日志，因此在 JWT 校验阶段被拒绝、尚未进入 Java 控制器的 401/403 请求也能
通过请求 ID 在 Gateway 日志中定位。

## 业务端管理入口

使用 `ADMIN` 账号登录后，可以从右上角进入 `/admin`。管理中心提供：

- 巡检任务和 AI 分析数量概览；
- 审计事件总量和最近 24 小时失败数；
- 按操作、结果和用户名筛选的审计列表；
- 请求 ID、客户端 IP、耗时和分页查询。

管理 API 为：

```text
GET /api/admin/overview
GET /api/admin/audit-logs
```

两个接口在 Gateway、Node BFF 和 Java API 三层都只允许 `ADMIN` 访问。
