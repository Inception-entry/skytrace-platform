# SkyTrace（天巡智控）

SkyTrace 是面向“无人机巡检、实时告警、AI 辅助分析与审计追溯”的全栈平台，中文产品名为“天巡智控”。它不是单一服务，而是一套由业务端、独立后台、网关、核心业务、AI 服务和本地基础设施组成的可运行架构。

当前平台版本：**0.2.2**（发版说明见 [`docs/releases/v0.2.2.md`](docs/releases/v0.2.2.md)）。

## 技术架构

```text
业务用户 ──> Vue 3 + Cesium ──> Nginx ──> Spring Cloud Gateway ──> NestJS BFF ──> Spring Boot
                                                                        │                 ├─ MySQL / Temporal
                                                                        │                 ├─ Keycloak（JWT）
                                                                        │                 └─ FastAPI AI ──> Ollama / Qdrant / Redis
后台用户 ──> React 管理端 ─────────────────────────────────────────────> NestJS Admin ──> PostgreSQL / MinIO

告警与证据：RabbitMQ 闭环 + MinIO 证据链
可观测性覆盖层：Prometheus、Grafana、Loki、Promtail、Alertmanager
```
- **业务端**：Vue 3、TypeScript、Cesium，提供三维巡检、告警、知识库、AI 对话和审计概览。
- **网关与身份**：Nginx 统一入口；Spring Cloud Gateway 执行 Keycloak JWT 校验、角色策略、Redis 限流、请求追踪与指标采集。
- **业务服务**：NestJS BFF 聚合 API、透传 SSE、推送 Socket.IO 事件；Spring Boot 负责任务、告警、知识库边界、审计与 Temporal 工作流。
- **AI 服务**：FastAPI、LangChain、Ollama 和 Qdrant 提供 RAG 知识库、流式问答与巡检分析；Redis 保存会话上下文；YOLO26 视觉推理（默认 mock，可选真实权重）。
- **独立后台**：React + Ant Design 管理端与 NestJS 管理服务，使用 PostgreSQL、Prisma 和 MinIO 管理用户、角色、菜单、操作日志及头像上传。
- **部署与运维**：Docker Compose 提供本地、预发、生产与监控覆盖层；CI 包含各服务测试、依赖/镜像安全扫描和全栈验收。

服务职责、调用链和现阶段边界见 [`docs/architecture.md`](docs/architecture.md)；鉴权配置见 [`docs/gateway.md`](docs/gateway.md)。

## 目录结构

```text
skytrace-platform/
├── frontend/                            # Vue 3 + Cesium 业务端（SkyTrace）
├── backend-node/                        # NestJS BFF、SSE 透传与 Socket.IO
├── gateway-java/                        # Spring Cloud Gateway：鉴权、路由、限流
├── backend-java/                        # Spring Boot：任务、告警、审计、Temporal
├── backend-ai/                          # FastAPI：LangChain、Ollama、Qdrant RAG
├── admin-frontend/                      # React + Ant Design 独立管理端
├── admin-service/                       # NestJS + Prisma 管理 API
├── deploy/
│   ├── docker-compose.yml               # 本地完整运行环境
│   ├── docker-compose.staging.yml       # 镜像化预发覆盖层与 Caddy HTTPS
│   ├── docker-compose.production.yml    # 生产资源限制与 Keycloak 配置
│   ├── docker-compose.monitoring.yml    # Prometheus、Grafana、Loki 等监控栈
│   ├── keycloak/                        # Realm 与本地测试用户配置
│   ├── mysql/                           # MySQL 初始化与迁移前置脚本
│   └── .env.example                     # 环境变量模板
├── docs/
│   ├── architecture.md                  # 当前服务职责、链路和边界
│   ├── gateway.md                       # Keycloak、JWT 和网关策略
│   ├── knowledge-base.md                # 知识库与 RAG 调用链
│   ├── security-audit-admin.md          # 业务端权限与审计
│   ├── temporal-integration.md          # Temporal 当前实现与演进方向
│   └── releases/
│       ├── v0.2.0.md                    # 0.2.0 发版说明
│       ├── v0.2.1.md                    # 0.2.1 补丁说明
│       └── v0.2.2.md                    # 0.2.2 品牌统一说明
├── scripts/
│   ├── skytrace.sh                      # 本地 Compose 管理和权限验收
│   ├── mysql-backup.sh                  # MySQL 备份
│   └── restore-backup.sh                # MySQL 恢复
└── README.md                            # 项目总览与运行说明
```

`target/`、`node_modules/` 等目录是本地构建产物或依赖目录，不属于源码结构。

## 环境要求

完整 Docker 部署：

- Docker
- Docker Compose

本地开发：

- JDK 21、Maven 3.9+
- Node.js 20+、npm
- 前端推荐 Node.js 22+
- Ollama，以及 `my-drone-expert` 和 `nomic-embed-text` 模型

## Docker 启动

1. 准备环境变量：

```bash
cp deploy/.env.example deploy/.env
```

2. 在 `deploy/.env` 中替换所有示例密码。至少应设置
   `KEYCLOAK_ADMIN_PASSWORD`、`KEYCLOAK_UAV_SERVICE_CLIENT_SECRET`、
   `KEYCLOAK_DEV_USER_PASSWORD`、数据库密码和 `ADMIN_JWT_SECRET`。

3. 首次运行或代码发生变化时，构建并启动全部服务：

```bash
./scripts/skytrace.sh rebuild
```

日常启动、查看状态和日志：

```bash
./scripts/skytrace.sh start
./scripts/skytrace.sh status
./scripts/skytrace.sh logs
```

同步本地 `OPERATOR`、`VIEWER` 权限验收账号：

```bash
./scripts/skytrace.sh auth-users
./scripts/skytrace.sh auth-verify
```

只操作单个服务：

```bash
./scripts/skytrace.sh rebuild backend-java
./scripts/skytrace.sh rebuild gateway
./scripts/skytrace.sh restart temporal-ui
./scripts/skytrace.sh logs backend-node
```

重启或停止全部服务：

```bash
./scripts/skytrace.sh restart
./scripts/skytrace.sh stop
```

Compose 会启动业务端、系统管理后台、Gateway、Node BFF、Java、AI、Keycloak 及其依赖服务。业务端由 Nginx 托管，并将 `/api/` 与 `/socket.io/` 统一代理到 Gateway，再由 Gateway 路由到 Node BFF。业务端审计中心为 `http://localhost:8888/audit`；系统管理后台通过 `http://localhost:8889` 访问，API 前缀为 `/admin-api`。

首次使用知识库前需要准备本地嵌入模型：

```bash
ollama pull nomic-embed-text
```

启动后访问 `http://localhost:8888/knowledge` 上传和检索文档。详细说明见 [`docs/knowledge-base.md`](docs/knowledge-base.md)。

### YOLO26 视觉推理

默认使用 `AI_VISION_BACKEND=mock`，不下载权重，便于 CI/本地。真实 YOLO26：

```bash
# 本地
cd backend-ai
uv sync --group vision
export AI_VISION_BACKEND=yolo26 AI_VISION_MODEL=yolo26n.pt AI_VISION_DEVICE=cpu

# Docker 镜像（体积较大，含 torch）
docker compose build --build-arg INSTALL_VISION=1 backend-ai
# deploy/.env 中设置 AI_VISION_BACKEND=yolo26
```

识别入口：`POST /api/detections/analyze`（也可经 BFF `POST /api/alarms/analyze`）。
命中映射类别后可自动投递 RabbitMQ 告警闭环。

> Gateway、Node、Java 与基础设施的宿主机端口只绑定到
> `127.0.0.1`。局域网/公网业务入口只有 Nginx。Vue 使用 Keycloak PKCE
> 登录，具体配置见 [`docs/gateway.md`](docs/gateway.md)。

> `scripts/skytrace.sh` 可以从任意目录调用，默认使用项目中的 `deploy/.env`
> 和 Compose 文件；CI 可以通过 `UAV_ENV_FILE` 指定一次性的隔离配置。
> 运行 `./scripts/skytrace.sh help` 可以查看全部子命令。

### 可选覆盖层

监控栈在本地默认不启动，可按需叠加：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.monitoring.yml up -d
```

预发和生产环境使用预构建镜像与 Caddy HTTPS 入口。生产部署还应叠加资源限制和生产模式 Keycloak：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.staging.yml \
  -f deploy/docker-compose.production.yml up -d --no-build
```

## 持续集成

`.github/workflows/ci.yml` 会在每次 push 和 Pull Request 时并行执行：

- Java 21：Spring Boot 与 Spring Cloud Gateway 的测试和 Maven 打包；
- Python 3.12：AI 服务测试；
- Node.js 20：BFF 与管理 API 的 lint、测试及依赖审计；
- Node.js 22：Vue 业务端和 React 管理端的类型检查、构建及依赖审计。

上述任务全部通过后，`Docker full-stack integration` 会继续执行：

- 根据锁文件构建 AI、Java、Gateway、Node BFF、业务端和管理端镜像；
- 对应用镜像执行 HIGH/CRITICAL 漏洞扫描；
- 使用运行时随机生成的密码启动隔离 Compose 环境，不读取仓库 Secret；
- 使用 Ollama Mock 完成 AI 服务依赖健康检查，不下载本地大模型；
- 执行 `ADMIN`、`OPERATOR`、`VIEWER` 三角色鉴权验收；
- 创建临时巡检任务并验证
  `Gateway → Node BFF → Java → Temporal → MySQL` 状态闭环；
- 失败时上传 Compose 状态、全量容器日志和镜像清单，并保留 7 天。

`main` 分支在 Publish 成功后还会触发 `Deploy (test)`。**没有测试服务器时无需配置任何 Secret**，该工作流会自动跳过远程部署并保持成功；本地开发使用 `./scripts/skytrace.sh rebuild` 即可。将来若有 VPS，在服务器执行 `scripts/staging-init.sh`，并在 GitHub `Environments → test` 中配置 `TEST_SSH_*` 与 `STAGING_DOMAIN` 后，同一工作流才会真正 SSH 部署。

## 本地开发

### Java 核心服务

默认启用 `local` profile，使用内存 H2 数据库，不依赖 MySQL。

```bash
cd backend-java
mvn spring-boot:run
```

访问：`http://localhost:8081/api/health`

H2 控制台：`http://localhost:8081/api/h2-console`

### Node.js BFF

Node 服务会通过 `JAVA_BASE_URL` 调用 Java 服务。本地 Java 默认端口是 `8081`，因此建议显式配置：

```bash
cd backend-node
npm install
JAVA_BASE_URL=http://localhost:8081 npm run start:dev
```

HTTP 健康检查：`http://localhost:3000/api/health`

Socket.IO 服务：`http://localhost:3001`

### Spring Cloud Gateway

本地开发时先启动 Redis、Java 和 Node BFF，然后运行：

```bash
cd gateway-java
mvn spring-boot:run
```

Gateway API：`http://localhost:8082/api`

健康检查：`http://localhost:8082/actuator/health`

### Vue/Cesium 前端

```bash
cd frontend
npm install
npm run dev
```

访问：`http://localhost:8888`

本地开发时使用 Vite 的 `8888` 端口；Docker 部署时由 Nginx 提供前端页面，宿主机端口由 `FRONTEND_PORT` 配置。

### 独立管理后台

管理后台与业务端分开部署，面向管理员和运维人员使用：

- `admin-frontend/`：React、TypeScript、Ant Design Pro 管理页面；
- `admin-service/`：NestJS 管理 API，使用 Prisma 访问 PostgreSQL；
- MinIO：保存管理端用户头像等后台文件；
- JWT + RBAC：控制用户、角色、菜单和接口权限；
- 操作日志：记录后台用户的重要管理操作。

本地分别启动管理 API 和管理页面：

```bash
cd admin-service
npm install
npx prisma generate
npm run start:dev
```

```bash
cd admin-frontend
npm install
npm run dev
```

默认访问地址：

```text
管理页面（系统管理后台）：http://localhost:8889
审计中心（业务端）：http://localhost:8888/audit
管理 API：http://localhost:3100/admin-api
```

Docker 部署时，管理页面和管理 API 会随完整 Compose 环境一起启动。首次使用前请确认
`deploy/.env` 中的 `ADMIN_JWT_SECRET`、PostgreSQL 和 MinIO 配置已经替换为本地或生产环境值。

管理后台支持以下核心功能：

- 用户登录、刷新令牌和修改个人资料；
- 用户、角色、菜单及角色权限分配；
- 仪表盘、操作日志和文件/头像上传；
- `ADMIN`、`OPERATOR`、`VIEWER` 三类角色的权限控制。

本地验收账号可以通过以下命令同步：

```bash
./scripts/skytrace.sh auth-users
./scripts/skytrace.sh auth-verify
```

生产环境必须修改默认账号、JWT Secret、数据库密码和 MinIO 凭据，不要直接使用
`deploy/.env.example` 中的示例值。

## 服务与端口

| 服务 | 默认地址 | 说明 |
| --- | --- | --- |
| Frontend / Nginx | `http://localhost:8888` | 统一业务访问入口 |
| HTTPS（预留） | `https://localhost:8443` | 使用 HTTPS 模板和证书后启用 |
| 审计中心 | `http://localhost:8888/audit` | 业务端 Keycloak 审计概览（ADMIN） |
| 系统管理后台 | `http://localhost:8889` | 独立用户、角色、菜单和日志管理端 |
| Spring Cloud Gateway | `http://localhost:8082` | 鉴权、路由、限流和访问日志 |
| Java API | `http://localhost:8081/api` | Docker 环境核心业务服务 |
| Java API（本地） | `http://localhost:8081/api` | local profile + H2 |
| Node API | `http://localhost:3000/api` | BFF 接口 |
| Socket.IO | `http://localhost:3001` | 实时告警推送 |
| Admin API | `http://localhost:3100/admin-api` | 独立后台 API |
| Keycloak | `http://localhost:8180` | 业务端 OIDC 身份中心 |
| AI API | `http://localhost:8000` | FastAPI 健康检查与 AI 调试入口 |
| MySQL | `localhost:3307` | 宿主机映射端口 |
| PostgreSQL | `localhost:5433` | 管理后台数据存储 |
| Redis | `localhost:6380` | 缓存、聊天记忆和 Gateway 限流 |
| Qdrant | `localhost:6333` | RAG 文档向量和元数据 |
| RabbitMQ | `http://localhost:15672` | 管理控制台 |
| MinIO API | `http://localhost:9011` | 对象存储 API |
| MinIO Console | `http://localhost:9012` | 对象存储控制台 |
| Temporal gRPC | `localhost:7233` | Temporal Server |
| Temporal UI | `http://localhost:8088` | 工作流可视化控制台 |
| Grafana（可选） | `http://localhost:3030` | 监控仪表盘 |
| Prometheus（可选） | `http://localhost:9090` | 指标查询 |

账号、密码和端口均以 `deploy/.env` 为准，请勿在生产环境继续使用示例密码。

## API 速查

### Java 服务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 健康检查 |
| `GET` | `/api/devices` | 设备列表（DB + Redis 在线状态） |
| `GET` | `/api/devices/{deviceCode}` | 设备详情 |
| `POST` | `/api/devices` | 创建设备（ADMIN/OPERATOR） |
| `PUT` | `/api/devices/{deviceCode}` | 更新设备名称/类型（ADMIN/OPERATOR） |
| `POST` | `/api/devices/{deviceCode}/heartbeat` | 设备心跳，写入 Redis 在线状态 |
| `GET` | `/api/inspection-tasks` | 巡检任务列表（含设备名与在线状态） |
| `POST` | `/api/inspection-tasks` | 创建巡检任务（设备须已存在） |
| `GET` | `/api/alarms/latest` | 最近 20 条告警 |
| `POST` | `/api/alarms` | 创建告警并写入数据库 |
| `POST` | `/api/alarms/detections` | 投递识别告警到 RabbitMQ（异步落库） |
| `GET` | `/api/evidence?taskCode=` | 按任务/告警查询证据列表 |
| `POST` | `/api/evidence` | 上传截图/视频证据到 MinIO，返回 object key |
| `GET` | `/api/knowledge/documents` | 查询知识文档 |
| `POST` | `/api/knowledge/documents` | 上传知识文档（ADMIN） |
| `POST` | `/api/knowledge/search` | 语义检索知识片段 |
| `DELETE` | `/api/knowledge/documents/{id}` | 删除知识文档（ADMIN） |
| `POST` | `/api/inspection-workflows/{taskCode}` | 启动巡检工作流 |
| `GET` | `/api/inspection-workflows/{taskCode}/status` | 查询巡检工作流状态 |
| `POST` | `/api/inspection-workflows/{taskCode}/complete` | 完成巡检工作流 |
| `POST` | `/api/inspection-workflows/{taskCode}/cancel` | 取消巡检工作流 |
| `POST` | `/api/inspection-workflows/{taskCode}/analysis` | Temporal 可靠分析，返回完整结果 |
| `POST` | `/api/inspection-workflows/{taskCode}/analysis/stream` | SSE 实时分析，逐段返回 Token |
| `GET` | `/api/inspection-tasks/{taskCode}/analyses` | 查询任务的 AI 分析历史 |

### Node BFF

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 健康检查 |
| `GET` | `/api/alarms/latest` | 转发 Java 最近告警接口 |
| `POST` | `/api/alarms` | 创建告警，并广播 `alarm.created` 事件 |
| `POST` | `/api/alarms/detections` | 投递识别告警到 RabbitMQ |
| `POST` | `/api/evidence` | 上传证据文件并转发 Java/MinIO |
| `GET` | `/api/evidence` | 按 taskCode/alarmEventCode 查询证据 |
| `POST` | `/api/inspection-tasks/{taskCode}/analysis/stream` | 透传 AI SSE 实时分析 |
| `GET` | `/api/inspection-tasks/{taskCode}/analyses` | 查询 MySQL 中的 AI 分析历史 |
| `GET` | `/api/inspection-tasks/{taskCode}/workflow-status` | 查询 Temporal 状态与最近告警 Signal |

创建告警示例：

```bash
curl -X POST http://localhost:3000/api/alarms \
  -H 'Content-Type: application/json' \
  -d '{
    "deviceCode": "UAV-001",
    "taskCode": "TASK-001",
    "eventType": "WEAPON_DETECTED",
    "weaponType": "KNIFE",
    "confidence": 0.96,
    "latitude": 31.2304,
    "longitude": 121.4737
  }'
```

Node BFF 会自动补充缺失的 `eventTime`。直接请求 Java 服务时，必须传入 ISO 格式的 `eventTime`。

## Socket.IO 事件

| 事件 | 方向 | 说明 |
| --- | --- | --- |
| `connected` | 服务端 -> 客户端 | 鉴权成功后返回客户端 ID、用户名、角色和 Token 到期时间 |
| `ping` | 客户端 -> 服务端 | 连通性测试 |
| `pong` | 服务端 -> 客户端 | 返回 `ping` 携带的数据 |
| `alarm.created` | 服务端 -> 客户端 | HTTP 创建或 RabbitMQ 实时事件触发后广播 |

## 当前实现边界

已实现：

- Java 告警持久化、最近告警查询、参数校验和统一响应。
- RabbitMQ 识别告警队列：AI/API 投递 → Java 落库 → Temporal `alarmDetected` Signal → Node Socket.IO 广播。
- MinIO 巡检证据上传：截图/视频只持久化 object key，经 `/files/` 反代访问。
- Flyway 管理 AI 分析结果表与证据资产表，持久化同步与 SSE 分析并支持历史查询。
- 设备主数据持久化（`device`）与 CRUD；列表在线状态由 Redis heartbeat 覆盖。
- 巡检任务绑定真实设备（创建/更新校验设备存在），响应带设备名与在线状态。
- 证据按任务/告警查询；任务页可选设备、查看与上传关联证据。
- Node 告警代理、证据上传代理、Socket.IO JWT 握手鉴权与实时广播。
- Vue 3 + Cesium 业务端，包含任务、告警、知识库、聊天、主题、布局和国际化能力。
- Temporal 巡检生命周期、同步 AI 分析和流式 AI 分析工作流。
- LangChain + Ollama + Qdrant 知识库，支持 PDF、Markdown、TXT 入库和可追溯 RAG 检索。
- `/chat` 使用 SSE 逐段显示 LangChain/Ollama 生成内容，并保留知识来源。
- React + NestJS 独立管理后台，包含本地登录、用户、角色、菜单、操作日志和 MinIO 头像上传。
- Docker Compose 的本地、预发、生产和可观测性覆盖层；CI 的全栈、权限、告警/证据链路验收；Publish 含 admin 镜像。
- YOLO26 视觉推理入口（默认 mock；可选 ultralytics 真实权重）与告警投递联动。
- Redis 最近告警缓存与设备 heartbeat 在线状态。

预留或待完善：

- Temporal Nexus 跨服务能力。
- 视频抽帧流水线与定制武器/缺陷数据集微调。
- 设备、航线、飞控和完整任务领域的深化与数据权限。
