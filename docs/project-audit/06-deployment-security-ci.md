# 06. 部署、安全与 CI 审计

实施状态：**审计建议尚未实施；审计后只为 Compose、Caddy、Dockerfile、脚本和 workflow 补充中文注释，未改有效配置或命令。Keycloak 严格 JSON 保持不变。**

## 1. 已验证的部署配置

以下 Compose 组合均用 `deploy/.env.example` 做了语法/合并解析：base、vision、MQTT、MQTT+TLS、monitoring、staging，以及文档规定的完整 production 链 `base + vision + staging + production`，均可解析。

`docker-compose.production.yml` 不是独立 overlay：它只补充部分已有服务，并引用 staging 提供的 `caddy`。单独与 base 合并会因 caddy 没 image/build 而失败；当前文件注释和发布脚本确实要求四层组合，因此不是隐藏语法错误，但建议把唯一合法组合封装成命令，避免人工漏层。

## 2. 生产身份与域名

### DP-01 / P0：生产复用包含开发用户的 Keycloak realm

`deploy/keycloak/skytrace-realm.json:90-143` 导入三个 enabled 用户：admin、operator、viewer；密码都来自同一个 `SKYTRACE_DEV_USER_PASSWORD`，且 `temporary:false`。生产 overlay `deploy/docker-compose.production.yml:42-54` 仍使用 `start --import-realm`，并继承 base 中的同一持久卷和 Realm 挂载（`deploy/docker-compose.yml:390-392`）。

风险不只在 JSON：Keycloak 对已存在 realm 通常不会按后续 import 自动删除/覆盖现有对象。因此只从模板删除用户，不代表已经部署的数据库内用户消失。

建议：

1. 拆 `skytrace-realm.local.json` 与不含用户/永久密码的 production realm。
2. 生产管理员通过一次性 bootstrap secret 创建，首次登录强制改密；普通用户由正式身份流程创建。
3. 对现有 realm 用 Admin Console/kcadm 盘点并禁用/删除开发账号，撤销 session，必要时轮换 client secret。
4. 生产发布预检调用 Admin API 断言不存在 `@local.test` 和已知开发用户名。

### DP-02 / P1：realm 只允许 localhost redirect URI

`deploy/keycloak/skytrace-realm.json:35-42` 只有 `localhost:8888` 和 `127.0.0.1:8888`。fresh staging/production realm 使用真实域名时，浏览器 redirect 会被拒绝，除非运维手工改过 realm。

建议把公开 URL 纳入环境专属 realm 配置/幂等 kcadm job。生产 URI 应精确到应用路径，避免宽泛 `https://*.example.com/*`。CI 在 fresh Keycloak 数据卷上完成真实域名替代后的 OIDC authorization-code + PKCE 流。

### DP-03 / P1：staging overlay 没有完整覆盖认证/CORS URL

`deploy/docker-compose.staging.yml:18-50` 只覆盖 MinIO public endpoint、Nginx server name 和 Keycloak hostname。下列值仍主要依赖宿主机 `.env` 手工保持一致：

- frontend `KEYCLOAK_PUBLIC_URL`
- Gateway/Java/Node issuer
- Gateway CORS origin
- Node WebSocket origin
- Keycloak realm redirect/webOrigins

`deploy/.env.example:54,56,61,75` 全是 localhost。漏改任一项都可能造成混合内容、401 issuer mismatch、CORS 或 WebSocket 失败。

建议 overlay 从一个经校验的 domain 派生完整配置：

```yaml
services:
  frontend:
    environment:
      KEYCLOAK_PUBLIC_URL: https://${SKYTRACE_DOMAIN:?SKYTRACE_DOMAIN is required}
  gateway:
    environment:
      GATEWAY_ALLOWED_ORIGIN: https://${SKYTRACE_DOMAIN:?}
      GATEWAY_JWT_ISSUER_URI: https://${SKYTRACE_DOMAIN:?}/realms/skytrace
  backend-java:
    environment:
      AUTH_JWT_ISSUER_URI: https://${SKYTRACE_DOMAIN:?}/realms/skytrace
  backend-node:
    environment:
      AUTH_JWT_ISSUER_URI: https://${SKYTRACE_DOMAIN:?}/realms/skytrace
      WS_ALLOWED_ORIGIN: https://${SKYTRACE_DOMAIN:?}
```

内部 JWK URL 可以继续走容器网络，但 token issuer 必须和外部签发值严格一致。

### DP-04 / P2：Admin UI 在远端环境没有公开入口

`deploy/docker-compose.staging.yml:41-46` 将 admin frontend 绑定 `127.0.0.1:8889`；`deploy/Caddyfile:1-13` 只路由 Keycloak 和主 frontend。结果是后台只能在服务器本机/SSH 隧道访问。

这可能是有意的安全选择，但当前没有明确写成产品边界。二选一：

- 需要公网管理：单独 `admin.${SKYTRACE_DOMAIN}`，独立 TLS/CSP/CORS、IP/VPN/SSO 控制。
- 只允许运维隧道：文档明确 SSH/VPN 步骤，并确认浏览器 API origin 可工作。

不要把 Admin 静默挂到主站未知路径，避免 cookie/CSP/路由边界混乱。

## 3. Edge、安全头与上传边界

### DP-05 / P1/P2：Caddy 缺统一 edge security policy

`deploy/Caddyfile:1-13` 只有压缩和 reverse proxy，没有 HSTS、nosniff、framing、referrer、permissions 等。即使下游 Nginx 设置了一部分，Keycloak 路径和直接由 Caddy 产生的响应仍可能不一致。

建议在真实域名预发先验证：

```caddyfile
header {
    Strict-Transport-Security "max-age=31536000; includeSubDomains"
    X-Content-Type-Options "nosniff"
    Referrer-Policy "strict-origin-when-cross-origin"
    Permissions-Policy "camera=(), microphone=(), geolocation=()"
}
```

HSTS `includeSubDomains` 只有确认所有子域长期 HTTPS 后才能打开。CSP 对主前端、Admin 和 Keycloak 应分别设计；一个过宽的全站 CSP 或直接复制 `unsafe-inline` 都不理想。

### DP-06 / P1：请求体上限在各层不一致

- Gateway `deploy/.env.example:73` 默认 20 MB。
- Java evidence request 默认 20 MB。
- AI 视频代码默认允许 50 MB。
- Admin Service 头像允许 2 MB，而 Admin Nginx 默认约 1 MB。

建议建立一张“路由级 payload budget”并自动测试：edge、Nginx、Gateway、BFF/Multer、Java/FastAPI、临时盘/对象存储必须一致或有意逐层收紧。错误都应返回稳定 413，而不是 Nginx HTML、Gateway 错误和后端 JSON 三种不兼容形式。

## 4. 发布与回滚

### DP-07 / P1：发布失败只回滚当前服务，留下混合版本

`scripts/deploy-production.sh:52-77` 在依赖顺序逐个更新。假设第 4 个服务失败，前三个已在新 tag；脚本只调用 `rollback_service "$svc"` 回滚第 4 个，最终是新旧混合版本。

最低兼容修复思路：记录已经成功更新的服务并逆序回滚：

```bash
updated_services=()

for svc in "${SERVICES[@]}"; do
  if compose up -d --no-deps --no-build "$svc" && wait_healthy "$svc"; then
    updated_services+=("$svc")
    continue
  fi

  for ((i=${#updated_services[@]}-1; i>=0; i--)); do
    IMAGE_TAG="$PREV_TAG" compose up -d --no-deps --no-build "${updated_services[$i]}"
    wait_healthy "${updated_services[$i]}" || record_rollback_failure
  done
  exit 1
done
```

这仍不是原子发布。更稳的是蓝绿 Compose project/主机、先完成整栈健康和冒烟，再让 Caddy 切 upstream。

### DP-08 / P1：生产 workflow 与 staging overlay 默认允许 `latest`

`.github/workflows/deploy-production.yml:4-9` 描述允许 `main-abc1234 or latest`，默认就是 `latest`；staging overlay 的七个应用镜像也都使用 `${IMAGE_TAG:-latest}`（`deploy/docker-compose.staging.yml:16,19,25,28,31,39,42`）。

`latest` 是可变引用，无法证明部署内容，也让 `.current-image-tag` 回滚不可靠。建议：

```bash
if [[ ! "$IMAGE_TAG" =~ ^main-[0-9a-f]{7,40}$ ]]; then
  echo "IMAGE_TAG must be immutable main-<git-sha>" >&2
  exit 2
fi
```

更强方案是 Publish 生成 release manifest，记录每个服务的 OCI digest；生产按 digest 部署并保存上一份 manifest。

### DP-09 / P1：数据库 migration 与代码回滚没有兼容门禁

Java Flyway 在启动时前进；Admin 容器启动时执行 `prisma migrate deploy`。若新版本先做破坏性 migration，再回滚旧镜像，旧代码不一定兼容。

要求 expand/contract：

1. 版本 N 只新增 nullable 列/表/索引，双写或兼容读。
2. 完成数据回填和监控。
3. 版本 N+1 切换读取。
4. 至少跨过一个安全回滚窗口后，版本 N+2 再删除旧结构。

发布脚本在迁移前备份只是必要条件，不等于可快速回滚；需要 restore 演练和明确 RPO/RTO。

### DP-10 / P2：发布脚本不验证基础设施/Caddy变更

`scripts/deploy-production.sh:66-79` 只滚动七个应用服务；不确认 mysql/redis/rabbit/minio/temporal/keycloak/caddy 正常，也不 reload 变更的 bind-mounted Caddyfile。可能应用健康但公网入口仍旧配置或缺失。

建议 preflight：Compose config、必填 secret、磁盘、Docker 版本、全部依赖 readiness、Caddy validate；配置 hash 改变时显式 graceful reload；发布后从公网域名做 OIDC/API/WebSocket/静态资源冒烟。

### DP-11 / P2：workflow 在生产机直接 hard reset 到 main

`.github/workflows/deploy-production.yml:61-69` SSH 后 `git reset --hard origin/main`，然后可以部署用户输入的旧 image tag。这会让“部署控制脚本/Compose 是当前 main，应用镜像是历史 SHA”，产生控制面与运行面版本错配。

建议 release bundle 把 Compose、Caddy、migration metadata 和镜像 digest 固定到同一 release SHA；部署那个 bundle，而不是永远取当前 main。生产 `.env`、备份和持久数据必须位于 repo 外且权限隔离。

## 5. Compose 可扩展性与资源

### DP-12 / P1/P2：固定 `container_name` 与全局 network name 阻碍横向扩展

`deploy/docker-compose.yml` 几乎每个服务指定 `container_name`；`deploy/docker-compose.yml:481-485` 又固定 `skytrace-edge`、`skytrace-backend`。

影响：

- `docker compose --scale` 对固定名字的服务无法创建多个容器。
- 同主机多个 project/蓝绿环境名字冲突。
- 测试虽设置 `COMPOSE_PROJECT_NAME`，固定资源仍削弱隔离。
- 与项目的水平扩展文档目标不一致。

建议移除 `container_name` 和 network `name`，使用 Compose service DNS；外部共享网络确有必要时用明确 external network，并把它与应用 project network 分开。

### DP-13 / P2：production 资源限额仍遗漏多个服务

`deploy/docker-compose.production.yml:16-87` 为部分服务设置 mem/cpu，但没有 qdrant、postgres、admin-service、admin-frontend、temporal-ui。后台上传/Prisma 或向量库异常时仍可能争抢宿主机资源。

建议先测量再设 requests/limits；同时配置日志轮转、进程 heap、临时盘配额和 OOM 告警。64 MiB Nginx 与 1.5 GiB AI 等值也应通过真实负载验证，不要只看容器能启动。

### DP-14 / P1/P2：内部依赖缺最小权限、认证或 TLS

- Redis/Qdrant 基础栈未配置认证/TLS。
- Java 和 Admin 都使用 MinIO root credentials，而不是应用专用 policy/user。
- Temporal 使用 MySQL root。
- MySQL JDBC 通用配置禁用 TLS。
- 管理服务数据库 URL直接插入密码；Rabbit/AI URL也直接插值，特殊字符可能破坏 URI。

loopback host port 降低公网暴露，但容器被攻陷后仍存在横向移动。建议：应用专属数据库/MinIO/Rabbit账号；Redis ACL/Qdrant API key；网络分段；生产内部 TLS 或受控私网；使用 secret files/manager。URI 中密码必须 percent-encode，或使用支持离散字段的客户端配置。

### DP-15 / P1：大量运行时容器默认 root

AI、Node、Admin Service、Java、Gateway Dockerfile 都未切换非 root；Nginx master root 是常见默认，但也可用 unprivileged image/高端口再由 edge 代理。

建议每个镜像固定 uid/gid、COPY chown、只读 rootfs、drop all capabilities、`no-new-privileges`，仅为 temp/archive/model cache 显式挂载可写目录。必须用真正工作流测试，尤其 Prisma migrate 和 evidence archive。

## 6. 镜像与供应链

### DP-16 / P1/P2：运行镜像使用 `latest` 或非 digest tag

`deploy/docker-compose.yml:83,99,120` 分别使用 `minio/minio:latest`、`temporalio/auto-setup:latest`、`temporalio/ui:latest`。其他基础镜像虽有版本 tag，也没有 digest。

建议至少固定明确版本，生产进一步固定 digest；由 Renovate/Dependabot 或定期 PR 更新，并跑数据兼容/回滚测试。Temporal `auto-setup` 更适合开发环境，生产应采用官方生产拓扑和显式 Schema 管理。

### DP-17 / P1：CI 从 `main` 下载 Trivy installer 并 pipe 到 shell

`.github/workflows/ci.yml:270-283`：

```bash
curl .../aquasecurity/trivy/main/contrib/install.sh | sh ... latest
```

这是高权限、可变上游脚本。建议固定 `aquasecurity/trivy-action` 的版本和 commit SHA，或下载固定 release asset 并校验 SHA256。`scripts/staging-init.sh:16` 的 `curl https://get.docker.com | sh` 也应替换为发行版仓库/固定官方安装步骤和版本验证。

### DP-18 / P2：GitHub Actions 只固定 major/tag，不固定 commit SHA

`actions/checkout@v6`、`setup-*`、`appleboy/ssh-action@v1`、Trivy action 等 tag 可移动。高保障生产 pipeline 建议 pin 到完整 commit SHA，并由自动化 PR 更新。第三方 SSH action 尤其需要审查权限、日志脱敏和 provenance。

### DP-19 / P2：缺统一 SBOM/provenance/release manifest

当前有镜像漏洞扫描，但发布建议进一步输出每个服务的 CycloneDX/SPDX SBOM、OCI digest、源码 SHA、build provenance 和扫描结果，形成签名 release manifest。生产部署和回滚只引用 manifest。

## 7. Secrets 与配置校验

### DP-20 / P1：示例弱凭据容易被误用，发布前无统一拒绝逻辑

`deploy/.env.example:1,4,8-9,14,16-17,19-20,76-79,115,139-147` 包含多组开发默认/占位值。示例本身可以为本地教学存在，问题是 production script 没有系统性检查是否仍使用它们。

建议 `scripts/validate-production-env.sh`：

```text
- 所有必填值存在且不等于已知默认/空字符串。
- access/refresh/client/broker/storage secret 长度和熵满足要求且彼此不同。
- production security enabled，MQTT TLS insecure=false。
- domain/url 为 HTTPS 且 issuer/origin/redirect 一致。
- IMAGE_TAG/REGISTRY 为允许格式。
- 密码包含 URI 特殊字符时不会破坏 DATABASE_URL/RABBITMQ_URL。
```

输出只列变量名和失败原因，绝不打印 secret。

### DP-21 / P2：MQTT TLS 示例默认跳过验证

`deploy/.env.example:146-147` 的 `MQTT_TLS_INSECURE=true` 适用于本地自签演练，不能流入生产。production preflight 应在 MQTT enabled 时强制 false、校验 CA/client identity 和 broker hostname。

## 8. CI 与依赖治理

### DP-22 / P1/P2：AI 安装不锁定 uv 工具，也没有 `--locked`

`.github/workflows/ci.yml:61-72` `pip install uv` 获取当时最新版，`uv sync --group dev` 允许 lock 更新检查语义漂移。建议使用官方 setup-uv action并固定版本/SHA，执行 `uv sync --locked --group dev`；另跑 `uv lock --check`。

### DP-23 / P2：Admin 前端 CI 没 lint 和行为测试

`.github/workflows/ci.yml:153-174` 只有 install/audit/build；虽然本地 lint 通过，CI 没运行它，且 package 没 test script。优先加入 lint、React Testing Library/MSW 的 auth 状态机测试和 Admin Playwright。

### DP-24 / P1/P2：依赖扫描和 Dependabot 覆盖不完整

- `.github/workflows/dependency-scan.yml:19-32` 只含 backend-java、gateway-java、backend-node、frontend、backend-ai，漏 admin-service、admin-frontend、e2e、device-sim。
- `.github/dependabot.yml:35-39` 同样漏多个 npm/Docker 目录。

这与当前发现吻合：Admin Service 有 7 个生产 advisory，却不在 scheduled Trivy FS matrix。应将所有 lockfile/manifest 自动枚举或有 CI 测试防止新子项目漏接。

### DP-25 / P2：CI 只验证 base Compose

`.github/workflows/ci.yml:256-261` 只解析 base；staging、production、vision、MQTT/TLS、monitoring overlay 不在门禁。建议 matrix 运行所有合法组合，并为必填变量使用合成非秘密值。

### DP-26 / P2：npm 审计阈值与实际风险不完全对应

当前 CI 多使用 `npm audit --audit-level=high --omit=dev`。这会允许 Admin 前端/服务的 moderate 生产 advisory，也完全忽略开发链供应链风险。

建议策略分层：

- PR：生产依赖 moderate+ 阻断或要求有到期豁免；dev high+ 阻断。
- scheduled：全依赖、容器、OS、secret、IaC 扫描。
- release：在 release SHA 重跑并归档 JSON/SARIF/SBOM。

`npm audit` 不是唯一来源，应与 Trivy/OSV/Dependabot 合并去重和适用性分析。

### DP-27 / P2：Java 质量门禁不足

两个 POM 没有覆盖率阈值、SpotBugs/PMD/format 或 OWASP dependency-check。建议先以不阻断方式采集基线，再对新增代码/高置信规则逐步设门禁；另加真 MySQL migration smoke。

## 9. 仓库卫生

### DP-28 / P3：误提交文件

以下都是 tracked：

- `%NVM_HOME%PATH.txt`：12 字节，内容仅 `PATH=%PATH%`。
- `install.cmd`：约 450,691 字节，实际是“App unavailable in region | Claude”HTML，不是 cmd 脚本。
- `frontend/src/assets/model/CesiumDrone.glb:Zone.Identifier`：Windows ZoneTransfer 旁车元数据。

建议单独 housekeeping PR 删除，并在 ignore/CI 增加规则，拒绝 `*:Zone.Identifier`、可疑根目录下载页和明显错误 MIME 的脚本文件。本次没有删除。

### DP-29 / P3：README Markdown 链接被反引号包裹

例如根 `README.md:5` 把 Markdown link 放进反引号，因此渲染成代码文本而不可点击。建议文档 lint（markdownlint + link checker）覆盖内部链接、锚点和误用 code span。

## 10. 建议 PR 拆分

1. `security(keycloak): split production realm and remove development identities`
2. `fix(deploy): derive all public auth/origin URLs from validated domain`
3. `fix(release): immutable image references and whole-deploy rollback`
4. `ops(db): establish expand-contract migration and restore gate`
5. `refactor(compose): remove fixed names and enable isolated blue-green projects`
6. `security(infra): least-privilege credentials, network segmentation and TLS`
7. `hardening(images): pinned images, non-root runtime and read-only filesystem`
8. `security(ci): pin installers/actions and publish SBOM/provenance`
9. `ci: cover all projects, lockfiles and Compose overlays`
10. `chore(repo): remove accidental artifacts and add doc/repo lint`
