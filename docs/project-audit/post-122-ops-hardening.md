# 1.2.2 之后的兼容收口

适用：当前 `main`。  
做了四件不改对外契约的事：refresh family 撤销、Admin 登录限流改走 Redis、已有 Keycloak 客户端对齐、历史操作日志和 detection 队列的核对脚本。

没有把告警时间改成 UTC，也没有把 Admin 登录改成 Cookie。那两项会破坏现有客户端，仍属于 `1.3.0`。蓝绿、基础镜像 digest、SBOM、Cesium 生命周期也不在这次。

## 代码

- `sys_refresh_token.family_id`：新登录带 family。已轮换过的 refresh 再拿来用，会删掉同一 family 的会话。没有 familyId 的旧 refresh 仍只返回 401。
- Admin `REDIS_HOST=redis`：多副本共享登录/刷新计数。Redis 连不上时 30 秒内退回本进程计数。

## 已有环境要跑的命令

生产部署脚本在健康检查之后会尝试对齐 Keycloak，并禁用 `skytrace-admin` / `operator` / `viewer`。预发只对齐 redirect，不禁用测试账号。

手工重跑：

```bash
SKYTRACE_DOMAIN=prod.example.com \
KEYCLOAK_ADMIN_USERNAME=... \
KEYCLOAK_ADMIN_PASSWORD=... \
  scripts/keycloak/reconcile-web-client.sh

KEYCLOAK_DISABLE_DEV_USERS=true \
KEYCLOAK_ADMIN_USERNAME=... \
KEYCLOAK_ADMIN_PASSWORD=... \
  scripts/keycloak/disable-dev-users.sh

cd admin-service && npx ts-node --transpile-only scripts/redact-operation-logs.ts
# 确认条数后
cd admin-service && npx ts-node --transpile-only scripts/redact-operation-logs.ts --apply

RABBITMQ_DEFAULT_USER=... RABBITMQ_DEFAULT_PASS=... \
  scripts/rabbit/redeclare-detection-queue.sh
# 只有确认可以丢掉队列里的未消费消息时：
SKYTRACE_REDECLARE_DETECTION_QUEUE=true \
  scripts/rabbit/redeclare-detection-queue.sh
```

改 JSON 或重跑导入仍然不会改掉已经进库的 Keycloak 用户和客户端。
