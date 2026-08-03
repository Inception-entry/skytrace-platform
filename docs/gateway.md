# Gateway 使用说明

## 请求链路

```text
浏览器
  -> Nginx :80/:443
  -> Spring Cloud Gateway :8080（容器内部）
  -> Node BFF :3000 / Socket.IO :3001
  -> Java :8080
```

Java、Node 和 Gateway 的宿主机调试端口只绑定到 `127.0.0.1`。
局域网或公网客户端只能通过 Nginx 进入业务系统，不能绕过 Gateway。

## 当前能力

- `/api/**` 路由到 Node BFF，`/socket.io/**` 路由到 Node 实时服务。
- Redis Token Bucket 限流：普通 API 默认每秒 20 个请求、突发容量 40；
  AI 巡检分析默认每秒 1 个请求、突发容量 2；WebSocket 握手默认每秒
  5 次、突发容量 10。登录后按用户限流，匿名请求按可信客户端 IP 限流。
- 限制单次请求体大小，默认 20MB。
- 生成或校验 `X-Request-Id`，记录客户端 IP、路由、状态码和耗时。
- 删除客户端伪造的身份请求头。
- 支持 JWT 签名、`iss`、`exp`、`nbf` 和可选 `aud` 校验。
- 支持 Keycloak realm role 和 client role。
- 401/403 使用统一 JSON 响应。
- 角色权限：`VIEWER` 只能读取 API，`OPERATOR` 和 `ADMIN` 可以修改业务数据，
  仅 `ADMIN` 可以访问受保护的 Actuator 端点。

## 本地启动 Keycloak

Vue 使用 Keycloak PKCE 登录，因此 Keycloak 会跟随普通 `skytrace.sh start` 一起启动。
也可以单独执行 `skytrace.sh auth-start`。

1. 在 `deploy/.env` 修改管理员密码：

   ```dotenv
   KEYCLOAK_ADMIN_USERNAME=admin
   KEYCLOAK_ADMIN_PASSWORD=请替换为强密码
   KEYCLOAK_DEV_USER_PASSWORD=请使用随机强密码
   ```

2. 启动 Keycloak：

   ```bash
   ./scripts/skytrace.sh auth-start
   ```

3. 访问 `http://localhost:8180`，进入 `skytrace` realm。首次启动会从
   `deploy/keycloak/skytrace-realm.json` 导入以下内容：

   - 公共客户端：`skytrace-web`，使用 Authorization Code + PKCE。
   - Realm 角色：`ADMIN`、`OPERATOR`、`VIEWER`。

4. 本地 realm 会创建三个开发测试用户，密码统一读取被 Git 忽略的
   `deploy/.env` 中的 `KEYCLOAK_DEV_USER_PASSWORD`：

   | 用户名 | 角色 | 用途 |
   | --- | --- | --- |
   | `skytrace-admin` | `ADMIN` | 审计中心、知识库管理和全部业务操作 |
   | `skytrace-operator` | `OPERATOR` | 任务操作和 AI 分析 |
   | `skytrace-viewer` | `VIEWER` | 任务与知识库只读访问 |

   已经导入过 realm 的现有环境不会重复执行导入，可以运行以下幂等命令同步
   测试用户、密码和角色：

   ```bash
   ./scripts/skytrace.sh auth-users
   ```

   执行三角色在 Gateway、Node BFF、Java API 三层的端到端权限验收：

   ```bash
   ./scripts/skytrace.sh auth-verify
   ```

   验收命令会临时启用本地 Password Grant 来获取测试 Token，并在退出时恢复
   `skytrace-web` 原有配置。正常前端登录始终使用 Authorization Code + PKCE。

   这些账号仅供本地开发和权限验收，生产环境不得使用。

## 获取服务调用 Token

Realm 会同时创建机密客户端 `skytrace-service`，用于脚本、Node 或其他后端服务
通过 `client_credentials` 获取 Bearer Token。它的 Service Account 默认只有
`OPERATOR` 角色，不能访问仅管理员可用的接口。

Client Secret 只保存在被 Git 忽略的 `deploy/.env`：

```dotenv
KEYCLOAK_SERVICE_CLIENT_SECRET=请使用随机强密钥
```

启动 Keycloak 并获取 Token：

```bash
./scripts/skytrace.sh auth-start
./scripts/skytrace.sh auth-token
```

第二条命令直接输出可用于访问 Gateway 的请求头内容：

```http
Authorization: Bearer <access_token>
```

可以直接保存请求头并调用 API：

```bash
AUTHORIZATION="$(./scripts/skytrace.sh auth-token)"
curl -H "Authorization: $AUTHORIZATION" \
  http://localhost:8082/api/inspection-tasks
unset AUTHORIZATION
```

## 开启 Gateway JWT 鉴权

在 `deploy/.env` 设置：

```dotenv
GATEWAY_SECURITY_ENABLED=true
GATEWAY_JWT_JWK_SET_URI=http://keycloak:8080/realms/skytrace/protocol/openid-connect/certs
GATEWAY_JWT_ISSUER_URI=http://localhost:8180/realms/skytrace
GATEWAY_JWT_AUDIENCE=skytrace-web
GATEWAY_JWT_CLIENT_ID=skytrace-web
```

然后重新创建 Node BFF、Gateway 和前端，使新的容器环境变量生效：

```bash
./scripts/skytrace.sh rebuild backend-node gateway frontend
```

Vue 启动后会跳转到 Keycloak，使用上述任一测试账号和 `deploy/.env` 中的
`KEYCLOAK_DEV_USER_PASSWORD` 登录。前端只使用 Authorization Code + PKCE，
不会持有 `skytrace-service` Client Secret。登录成功后，每次 API 请求都会自动刷新
并携带 Access Token。

Socket.IO 使用 `handshake.auth.token` 传递同一个短期 Access Token，Token 不会
出现在 URL 或代理访问日志中。Gateway 保留 `/socket.io/**` 的传输层路由与限流，
Node BFF 在 Socket.IO 中间件中通过 Keycloak JWKS 校验 RS256 签名、Issuer、
Audience、有效期及业务角色。Token 到期后服务端会断开连接，Vue 刷新 Token 后
自动重连。

REST 请求最终返回 `401` 或 `403` 时，Vue 分别进入 `/401`、`/403` 页面；401
页面支持强制重新登录，403 页面会展示当前用户和业务角色。

开启后，以下路径保持公开：

- `/api/health`
- `/actuator/health`
- `/actuator/info`
- `/socket.io/**`
- CORS `OPTIONS` 预检请求

其余路径必须携带：

```http
Authorization: Bearer <access-token>
```

业务角色规则：

| 请求 | 允许角色 |
| --- | --- |
| `GET/HEAD /api/**` | `VIEWER`、`OPERATOR`、`ADMIN` |
| `POST/PUT/PATCH/DELETE /api/**` | `OPERATOR`、`ADMIN` |
| `/actuator/**`（除 health/info） | `ADMIN` |

超过限流阈值时 Gateway 返回 HTTP `429 Too Many Requests`，并返回
`X-RateLimit-*` 响应头。AI 分析阈值可通过
`GATEWAY_AI_RATE_LIMIT_REPLENISH_RATE` 和
`GATEWAY_AI_RATE_LIMIT_BURST_CAPACITY` 单独调整。

`/socket.io/**` 在 Gateway HTTP 安全规则中保持公开，只表示允许 Engine.IO
建立传输连接。Socket.IO namespace 握手仍必须在 `auth.token` 中提供 JWT，
Node BFF 验签和角色校验失败时会拒绝连接。这样可以避免把 Token 放入查询参数。

## 传给 BFF 的可信身份

JWT 验证成功后，Gateway 会重新生成以下请求头：

```text
X-Authenticated-User       JWT sub，稳定用户 ID
X-Authenticated-Username   preferred_username
X-Authenticated-Roles      ADMIN,OPERATOR,VIEWER
```

客户端直接发送的同名请求头会先被删除，因此 BFF 只能信任来自 Gateway
内部网络的这些请求头。BFF 仍应根据角色执行页面级业务权限判断，Java 则负责
最终的数据权限和核心业务授权。

## 生产环境注意事项

- Keycloak 的 `start-dev` 和本地 realm import 只用于开发环境。
- 生产环境应使用 Keycloak 生产模式、独立数据库、HTTPS 和真实域名。
- 不要把 Keycloak 管理控制台暴露在公共业务域名上。
- 将 `NGINX_SERVER_NAME`、CORS 来源、Keycloak issuer 和前端回调地址换成
  同一个真实 HTTPS 域名体系。
- Gateway 宿主机的 `8082` 仅用于本机排障，公网只开放 Nginx 的 80/443。
