# SkyTrace 运维手册（1.0）

面向预发/生产值守的操作说明。本地开发速查仍可参考文末「本地开发」一节。

## 1. 职责与范围

| 角色 | 职责 |
| --- | --- |
| 值班工程师 | 监控 Grafana / Alertmanager，处理告警，执行备份与回滚 |
| 发布工程师 | 按 `IMAGE_TAG` 部署预发/生产，验证健康检查 |
| 安全联络 | 密钥轮换、Webhook 目标变更 |

覆盖：业务 Compose 栈（不含独立飞控）。Keycloak / MySQL / MinIO / Temporal 均在同一部署拓扑内。

## 1.1 品牌化与栈统一（`uav*` → `skytrace*`）

运行时契约已统一为 SkyTrace 标识，例如：

- Keycloak realm：`skytrace`；客户端：`skytrace-web` / `skytrace-service`
- JWT issuer / audience、RabbitMQ、Temporal 队列、Redis key、MinIO/Qdrant 桶名等
- 部署目录默认 `/opt/skytrace`；域名变量 `SKYTRACE_DOMAIN`

**本地若仍混有历史 `uav-*` 容器**，先清干净再拉本仓栈（否则会出现连错网络/旧库名）：

```bash
# 停掉本仓 compose（按你实际用的 env/compose 文件调整）
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down

# 删除历史 uav-* 容器（勿误删其他项目）
docker ps -a --format '{{.Names}}' | grep -E '^uav-' | xargs -r docker rm -f

# Keycloak realm 从 uav 迁到 skytrace：需重建 keycloak 数据卷后重新 import
docker volume ls | grep keycloak
# 确认卷名后：docker volume rm <skytrace或deploy>_keycloak_data
```

设备编号如 `UAV-001`、类型 `UAV` 属于业务域语言，**保留**。

## 2. 端口与绑定

- 业务入口默认 `127.0.0.1:8888`（生产经 Caddy HTTPS）
- 系统管理后台 `127.0.0.1:8889`
- Grafana `127.0.0.1:3030`（monitoring overlay）
- Alertmanager `127.0.0.1:9093`
- 对外发布前确认 Compose 端口绑定与防火墙

## 3. 密钥清单

在 `deploy/.env` 中至少配置（勿提交真实值）：

- `KEYCLOAK_ADMIN_PASSWORD`
- `KEYCLOAK_SERVICE_CLIENT_SECRET`
- `KEYCLOAK_DEV_USER_PASSWORD`（仅开发/CI）
- MySQL / PostgreSQL 密码
- `ADMIN_JWT_SECRET` / `ADMIN_JWT_REFRESH_SECRET`
- `ALERTMANAGER_WEBHOOK_URL`（生产必填真实 Webhook）
- `GRAFANA_ADMIN_PASSWORD`
- `MYSQL_DATABASE=skytrace_inspection`

## 4. 视觉推理（生产默认）

- **本地 / CI**：默认 `AI_VISION_BACKEND=mock`，不下载 YOLO 权重
- **预发 / 生产**：`scripts/deploy-staging.sh` / `deploy-production.sh` **强制**挂载 `docker-compose.vision.yml`；Publish 流水线对 `backend-ai` 构建 `INSTALL_VISION=1`
- 运行时默认 `AI_VISION_BACKEND=yolo26`、`AI_VISION_MODEL=yolo26n.pt`
- 本地验证真实 YOLO：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.vision.yml \
  up -d --build backend-ai
```

首次推理可能下载权重，需可出网或预置模型文件。

## 5. 部署与回滚

### 预发

```bash
export IMAGE_TAG=main-<sha> REGISTRY=ghcr.io/<org>/skytrace-platform SKYTRACE_DOMAIN=test.example.com
./scripts/deploy-staging.sh
```

失败时脚本会尝试回滚到 `.current-image-tag` 记录的上一版本。

### 生产（滚动）

```bash
export IMAGE_TAG=main-<sha> REGISTRY=ghcr.io/<org>/skytrace-platform SKYTRACE_DOMAIN=prod.example.com
./scripts/deploy-production.sh
```

逐服务重启并健康检查；单服务失败则回滚该服务到上一 `IMAGE_TAG`。

### 手动回滚

1. 确认目标 tag：`cat .current-image-tag` 与 GHCR 可用 tag
2. `IMAGE_TAG=<previous> ./scripts/deploy-production.sh`（或 staging）
3. 验证：`/gateway-health`、关键 Java `/api/health`、关键任务列表
4. 若涉及 Flyway 不兼容的降级，**禁止**直接回滚代码；先评估迁移可逆性或恢复 DB 备份

## 6. 备份与恢复

### MySQL 业务库

```bash
# 导出 → gzip → MinIO（路径 ${MYSQL_DATABASE}/<timestamp>.sql.gz）
./scripts/mysql-backup.sh

# 列出并恢复
./scripts/restore-backup.sh
# 或指定：
# RESTORE_FILE=skytrace_inspection/20240101T120000Z.sql.gz ./scripts/restore-backup.sh
```

建议：

- 生产每日定时（cron / systemd timer）跑 `mysql-backup.sh`
- RPO 目标：≤ 24h（按保留天数 `BACKUP_RETAIN_DAYS` 调整）
- 恢复后：重启 `backend-java`，检查 Flyway 历史与 `GET /api/devices`

### 其他组件

| 组件 | 建议 |
| --- | --- |
| Keycloak（MySQL `keycloak` 库） | 与业务库同实例时一并 dump，或单独备份 |
| PostgreSQL（admin） | 使用 `pg_dump`；未内置脚本时按实例备份策略执行 |
| MinIO 证据桶 | 开启版本控制或跨区域复制；证据不可仅依赖 DB |
| Temporal | 生产依赖 MySQL；备份同业务库策略 |

恢复演练：每季度至少一次在预发从 MinIO 拉备份恢复并验收登录 + 任务列表。

## 7. 监控与告警值班

### 栈启动

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.monitoring.yml \
  up -d
```

### Grafana

- 数据源：Prometheus / Loki（自动 provisioning）
- 预置：`SkyTrace Overview`（存活、HTTP 5xx、JVM Heap）
- 账号：`admin` / `GRAFANA_ADMIN_PASSWORD`

### Alertmanager

- 模板：`deploy/alertmanager/alertmanager.yml.template`
- 生产必须设置 `ALERTMANAGER_WEBHOOK_URL`（钉钉 / Slack / PagerDuty 等）
- 未配置时回落本地占位，**不会**真正通知

### 值班流程（最小）

1. **接收**：Webhook / 电话；记录告警名、实例、开始时间
2. **分级**：
   - P1：网关/Java/Keycloak 不可用，业务入口 5xx 飙升 → 15 分钟内响应
   - P2：单服务重启循环、磁盘/内存告警 → 1 小时内响应
   - P3：非核心指标、短暂抖动 → 下一工作日
3. **处置**：查 Grafana → `docker compose logs <svc>` → 必要时滚动重启或回滚
4. **关闭**：确认指标恢复后在告警渠道回复「已恢复」+ 简要原因
5. **升级**：P1 超 30 分钟未恢复 → 通知发布/架构联络人

## 8. 浏览器验收

```bash
cd e2e
npm install
npx playwright install chromium
E2E_BASE_URL=http://127.0.0.1:8888 \
KEYCLOAK_DEV_USER_PASSWORD=... \
KEYCLOAK_CLIENT_SECRET=... \
npm test
```

覆盖：入口冒烟、Keycloak 登录、任务创建/启动、证据上传、告警落库闭环。CI 的 Docker full-stack job 会跑同一套。

## 9. 本地开发

```bash
cp deploy/.env.example deploy/.env
./scripts/skytrace.sh rebuild
./scripts/skytrace.sh status
```

监控 / 视觉 overlay 仍按需叠加；本地默认可保持 mock 视觉后端。
