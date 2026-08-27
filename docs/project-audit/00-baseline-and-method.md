# 00. 基线与审计方法

实施状态：**审计建议仍未实施；审计后仅新增中文注释、文档字符串和必要的 EOF 换行，未改动有效逻辑、配置或命令。**

## 1. 审计基线

| 项目 | 值 |
| --- | --- |
| 日期 | 2026-08-24（Asia/Shanghai） |
| 分支 | `main` |
| HEAD | `2c89349` |
| 最近正式 tag | `v1.2.1` |
| Git 描述 | `v1.2.1-11-g2c89349-dirty` |
| 平台声明版本 | 各子项目均为 `1.2.1` |
| AI OpenAPI 显示版本 | `backend-ai/app/main.py:123` 仍硬编码 `0.1.0` |

下列清单描述审计开始前和全部只读验证刚结束时的工作区基线，不代表后续注释完善后的当前状态：

```text
 M README.md
 M docs/architecture.md
 M docs/horizontal-scaling.md
 M docs/mqtt-device-sim-guide.md
?? docs/mqtt-single-subscriber.md
```

审计与验证阶段除新增本审计目录外，没有保留源码、配置或测试改动。此后全仓仅新增中文注释、文档字符串和必要的 EOF 换行；这些后续改动没有改变有效逻辑、配置或命令，也没有实施本报告建议。

## 2. 覆盖范围

### 2.1 已检查

- `backend-java/`：Spring Boot、JPA/Flyway、MinIO、Temporal、MQTT、RabbitMQ、证据归档和鉴权。
- `gateway-java/`：路由、限流、请求体上限、CORS、JWT 和 Actuator。
- `backend-node/`：NestJS BFF、JWT/JWKS、Socket.IO、RabbitMQ、上传转发和 DTO。
- `admin-service/`：本地 JWT、refresh session、RBAC、审计日志、Prisma、上传、seed 和 Docker 启动。
- `frontend/`：Vue、Keycloak、Cesium、SSE、轮询、API 错误模型、可访问性和构建体积。
- `admin-frontend/`：React、token 刷新/登出、RBAC 菜单、请求竞态、Nginx 和测试缺口。
- `backend-ai/`：FastAPI、Ollama、Redis、Qdrant、PDF、视频/图片推理、RabbitMQ 和 RAG。
- `deploy/`、`scripts/`、`.github/`：Compose overlays、Caddy、Keycloak realm、发布/回滚、依赖扫描和 CI。
- 根目录和资源目录：误提交产物、版本一致性、文档链接和仓库卫生。

### 2.2 未覆盖或只能部分覆盖

- 没有连接真实生产数据库、Keycloak、RabbitMQ、MinIO、Qdrant、Ollama、Temporal 或 MQTT Broker。
- 没有启动完整 Docker 栈，因此只发现了 6 条 Playwright 用例，没有执行浏览器 E2E。
- 没有真实 GPU/YOLO 模型、超大视频、恶意 PDF、并发上传或长时间内存压力测试。
- 没有生产流量、SLO、真实审计日志和历史数据库可供核对。
- 未在本机完成 Java CVE 数据库扫描或镜像 Trivy 扫描；相关 CI 覆盖缺口已单列。
- 手工审计不能数学上证明“零遗漏”。本报告的“全部”表示本次范围内确认的全部问题，并给出证据与优先级。

## 3. 实际执行结果

### 3.1 测试

| 模块 | 命令 | 结果 |
| --- | --- | --- |
| AI | `.venv/bin/pytest tests -q` | 17 passed |
| Java 后端 | `mvn test -q` | 34 个 suite、110 tests，0 failure/error |
| Gateway | `mvn test -q` | 4 个 suite、11 tests，0 failure/error |
| Node BFF | `npm test -- --test-reporter=spec` | 13 passed |
| Admin Service | `npm test -- --runInBand` | 2 suites、24 passed |
| 主前端 | `npm test` | 4 个测试文件通过 |
| E2E | `npx playwright test --list` | 发现 3 个文件、6 条 Chromium 用例；未执行 |

Java/Node 第一次在受限沙箱内运行时，分别因 Maven 缓存只读和禁止监听 `127.0.0.1` 报错；在只读放开对应系统权限后均通过。这两次初始错误不属于业务断言失败。

### 3.2 lint、编译与配置

| 模块 | 检查 | 结果与告警 |
| --- | --- | --- |
| 主前端 | lint + production build | 通过；JS chunk 约 691.88 kB，超过 500 kB 告警；`config.js` 非 module 提示 |
| Admin 前端 | lint + production build | 通过；JS chunk 约 1,389.03 kB；ESM package type 告警 |
| Node BFF | lint + build | 通过；测试运行有 `MODULE_TYPELESS_PACKAGE_JSON` 告警 |
| Admin Service | lint + build | 通过 |
| Compose base/vision/MQTT/MQTT-TLS/monitoring/staging | `docker compose ... config --quiet` | 通过 |
| 完整生产 overlay 链 | base + vision + staging + production | 使用示例必填变量解析通过 |
| AI lock | `uv lock --check` | 通过 |

Gateway 测试虽然通过，但 Spring 明确输出大量配置迁移告警：`spring.cloud.gateway.*` 已临时映射到 `spring.cloud.gateway.server.webflux.*`。这不是当前启动失败，却是升级风险。

### 3.3 2026-08-24 在线依赖漏洞审计

| 模块 | 生产依赖 | 全依赖 | 重点 |
| --- | --- | --- | --- |
| `frontend` | 0 | 0 | 当前 npm advisory 无发现 |
| `e2e` | 0 | 0 | 当前 npm advisory 无发现 |
| `backend-node` | 0 | 1 high | high 位于开发依赖 `brace-expansion` 链 |
| `admin-frontend` | 2 moderate | 3 total：1 high、2 moderate | 生产链为 `react-router` / `react-router-dom`；开发链另有 `nanoid` |
| `admin-service` | 7 moderate | 20 total：4 high、13 moderate、3 low | 生产链涉及 Nest core/platform、Express/body-parser/qs、file-type |
| `backend-ai` | 3 advisories / 2 packages | 同左 | `pypdf 6.14.2` 两个恶意 PDF 资源耗尽漏洞；`h2 4.3.0` 一个重复 Host/request-smuggling primitive |

AI 锁文件确认包含上述版本：`backend-ai/uv.lock:312-313` 为 `h2 4.3.0`，`:1094-1095` 为 `pypdf 6.14.2`。建议升级目标至少为 `h2 4.4.1`、`pypdf 6.15.0`，再重新锁定和回归。

依赖审计是时间快照；任何发版都应在 release SHA 上重新执行，而不是复用本报告结果。

## 4. 证据规则

每条问题尽量包含：

1. 严重度与问题描述。
2. 当前代码 `path:line`。
3. 可触发条件与影响。
4. 短期兼容修复。
5. 长期设计方向。
6. 建议代码或伪 diff。
7. 必补测试与验收条件。

若某项取决于部署方式，会标为“条件性风险”，而不是把推断写成既成事实。

## 5. 不应误读的地方

- “测试通过”只代表已有测试覆盖的行为通过，不代表不存在安全或并发缺陷。
- “建议代码”不是可直接盲贴的最终补丁；跨服务协议、数据库时区和认证 Cookie 需要先形成 ADR。
- `npm audit` 的 package 计数会把传递关系重复映射为多项，不能直接等同于 20 个可独立利用漏洞。
- 浏览器中的 Cesium Ion token 本来就会暴露给客户端；问题是权限范围、域名限制和可轮换性，不应假装可以把它做成前端 secret。
