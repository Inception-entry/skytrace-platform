# 运维速查（Sprint D 最小片）

本文覆盖本地/预发常用运维动作，不替代完整生产手册。

## 端口与绑定

- 业务入口默认绑定 `127.0.0.1:8888`
- 系统管理后台 `127.0.0.1:8889`
- 监控 Grafana `127.0.0.1:3030`（需 monitoring overlay）
- 对外发布前请确认 Compose 端口绑定与防火墙策略

## 密钥清单

在 `deploy/.env` 中至少配置：

- `KEYCLOAK_ADMIN_PASSWORD`
- `KEYCLOAK_UAV_SERVICE_CLIENT_SECRET`
- `KEYCLOAK_DEV_USER_PASSWORD`
- 数据库密码（MySQL / PostgreSQL）
- `ADMIN_JWT_SECRET` / `ADMIN_JWT_REFRESH_SECRET`
- `ALERTMANAGER_WEBHOOK_URL`（监控告警回调）

不要把真实密钥提交到 Git。

## 启动与重建

```bash
cp deploy/.env.example deploy/.env
./scripts/skytrace.sh rebuild
./scripts/skytrace.sh status
```

监控覆盖层：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.monitoring.yml \
  up -d
```

真实 YOLO（可选，体积大）：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.vision.yml \
  up -d --build backend-ai
```

## Alertmanager

- 模板：`deploy/alertmanager/alertmanager.yml.template`
- 通过 `ALERTMANAGER_WEBHOOK_URL` 注入真实 Webhook（钉钉/Slack/PagerDuty 等）
- 未配置时回落到本地占位地址，避免误发

## Grafana

- 数据源：Prometheus / Loki（自动 provisioning）
- 预置仪表盘：`SkyTrace Overview`（服务存活、HTTP 5xx、JVM Heap）
- 默认账号 `admin`，密码来自 `GRAFANA_ADMIN_PASSWORD`

## 备份与回滚

```bash
./scripts/mysql-backup.sh
./scripts/restore-backup.sh
```

回滚优先：换回上一版镜像 tag，确认 Flyway 版本兼容后再切流量。

## 浏览器冒烟

```bash
cd e2e
npm install
npx playwright install chromium
E2E_BASE_URL=http://127.0.0.1:8888 npm test
```

该冒烟不覆盖完整 Keycloak 登录流，只验证入口与 `/drone` 可达。
