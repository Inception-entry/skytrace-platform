# 架构说明

## 当前职责边界

Nginx 负责：

- 业务端静态资源托管与缓存
- 将 `/api/` 和 `/socket.io/` 转发给 Spring Cloud Gateway
- 上传大小、代理超时和基础安全响应头

Spring Cloud Gateway 负责：

- Keycloak JWT 的签名、issuer、audience 与角色校验（默认开启）
- Node BFF API 与 WebSocket 服务路由
- 基于 Redis 的请求限流
- 统一请求 ID、访问日志和网关指标
- 将可信用户 ID、用户名和角色传给 BFF

Node BFF 负责：

- 面向业务端的 API 聚合与 Java 服务调用
- Socket.IO 告警推送和握手 JWT 校验
- AI SSE 响应透传

Spring Boot 负责：

- 巡检任务、设备、告警、知识库与 AI 分析历史
- Keycloak JWT 二次校验、核心业务授权与关键操作审计
- MySQL 持久化、Flyway 迁移和 Temporal Worker
- 启动及推进巡检、同步分析和流式分析工作流

Python AI 服务负责：

- LangChain + Ollama 本地对话和任务分析
- 使用 LangChain `astream` 输出 SSE Token
- Qdrant 文档向量化、语义检索和来源追踪
- Redis 聊天会话上下文

独立管理后台负责：

- React 管理端：用户、角色、菜单和操作日志界面
- NestJS 管理服务：本地 JWT 登录、RBAC、Prisma 数据访问和操作日志
- PostgreSQL：管理后台独立数据存储
- MinIO：当前用于管理员头像上传

## 当前通信方式

```text
Vue/Cesium -> Nginx -> Gateway -> Node BFF -> Spring Boot
                                      │              ├-> MySQL
                                      │              ├-> Temporal
                                      │              └-> FastAPI AI -> Ollama / Qdrant / Redis
                                      └-> Socket.IO

React Admin -> Admin Nginx -> Admin NestJS -> PostgreSQL
                                             └-> MinIO（头像）
```

交互式 AI 分析通过 `Vue -> Nginx -> Gateway -> Node -> Java -> AI` 的
SSE 链路流式返回。每个模型 Token 不写入 Temporal 历史；Java 会在完整回答
成功写入 MySQL 后再发送完成事件。同步分析由 Temporal Workflow 编排，以取得
可重试、可追踪的执行记录。

两条分析通道都会在模型完整返回后由 Java 服务写入
`ai_analysis_result` 表。Temporal 通道使用 Workflow ID 作为唯一
`analysis_id`，SSE 通道使用独立 UUID；因此 Activity 重试不会产生
重复记录，连接中断或模型失败也不会保存为成功结果。

业务端与后台认证是两套边界：业务端使用 Keycloak OIDC；独立管理后台使用自身
的 NestJS JWT 和 PostgreSQL RBAC，不经业务 Gateway。

RabbitMQ 与面向巡检的 MinIO 证据链已在 Compose 和配置中预置，但尚未接入
业务告警链路。

## 网关预置配置

默认访问链路：

```text
http://localhost:8888/api/**
  -> Nginx
  -> Spring Cloud Gateway
  -> Node BFF
  -> Java
```

Gateway 默认在宿主机 `8082` 端口开放调试入口，并提供：

- `GET /actuator/health`：健康检查。
- `/api/**`：转发到 Node BFF 的 `3000` 端口。
- `/socket.io/**`：转发到 Node 实时服务的 `3001` 端口。
- 默认每个用户或客户端 IP 每秒补充 20 个令牌，突发容量 40。

开启 JWT 鉴权前，需要先准备兼容 JWT/JWK 的身份中心，然后设置：

```dotenv
GATEWAY_SECURITY_ENABLED=true
GATEWAY_JWT_JWK_SET_URI=https://auth.example.com/realms/uav/protocol/openid-connect/certs
GATEWAY_JWT_ISSUER_URI=https://auth.example.com/realms/uav
GATEWAY_JWT_AUDIENCE=uav-web
```

无论 JWT 是否启用，Gateway 都会删除客户端传入的
`X-Authenticated-User`、`X-Authenticated-Username` 和
`X-Authenticated-Roles`。完整的本地 Keycloak 启用步骤见
[`docs/gateway.md`](gateway.md)。

Nginx 默认使用 HTTP 配置。启用 HTTPS 时：

1. 将 `frontend/nginx.https.conf.example` 复制为 `frontend/nginx.conf`。
2. 将证书分别放到 `deploy/nginx/certs/fullchain.pem` 和
   `deploy/nginx/certs/privkey.pem`。
3. 在 `deploy/.env` 配置真实 `NGINX_SERVER_NAME`、
   `GATEWAY_ALLOWED_ORIGIN` 和 `HTTPS_PORT=443`。
4. 执行 `./scripts/uav.sh rebuild frontend`。

## Temporal 现状与后续

Temporal 已接入 Spring Boot 进程。当前 Worker 在
`uav-inspection-task-queue` 注册巡检生命周期、同步 AI 分析和聊天分析
Workflow；业务端仍经由 Node BFF 调用 Java，Temporal 不直接暴露给前端。

现有巡检接口可启动、查询、完成或取消工作流。同步 AI 分析走 Workflow，
而 SSE 分析不将逐 token 输出写入 Workflow 历史。

Temporal 不替代 MySQL、RabbitMQ、MinIO 或 Socket.IO。后续应把
RabbitMQ 的 AI 识别结果转换为 Workflow Signal，并将 MinIO 中的证据对象 key
与任务状态关联。等飞控、视觉推理或证据服务成为拥有独立 Worker 的服务后，再
考虑用 Nexus 表达跨 Temporal 应用的可靠调用。
