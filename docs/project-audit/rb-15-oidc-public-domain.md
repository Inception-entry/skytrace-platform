# RB-15 / DP-02 / DP-03（真实域名 OIDC）实施说明

适用分支：`fix/oidc-public-domain-urls`  
只修 **staging/production 的公开认证 URL**：redirect/webOrigin、前端 Keycloak URL、Gateway CORS、Java/Node issuer 都从 `SKYTRACE_DOMAIN` 派生。  
**不要**做 Java/Node 上传、`h2`、整栈回滚、Admin 公网入口、版本号。

旧编号：RB-15、DP-02、DP-03。上一刀是 AI 像素预算（`#176`）和 `v1.2.2-rc.12`。

---

## 1. 一句话

Keycloak realm 只允许 `localhost:8888`。staging overlay 只改了 MinIO、Nginx server name 和 `KC_HOSTNAME`，issuer/CORS/前端 Keycloak URL 仍看宿主机 `.env`。fresh 域名环境登录会被 redirect 拒绝，或出现 issuer/CORS 混用。

本分支：realm 用环境变量占位；staging overlay 从一个真实 hostname 展开整套公开 URL。内部 JWKS 仍走容器网络。

---

## 2. 现在怎么坏的

```text
浏览器 https://test.example.com
        │
        ▼
Keycloak 只登记 http://localhost:8888/*
        │
        ▼
redirect_uri mismatch / CORS / issuer 仍是 localhost
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 把 Admin 挂到公网 | DP-04，独立 P2 |
| Java/Node 上传、`h2`、整栈回滚 | 各自独立 |
| 改内部 JWKS 为公网 URL | token issuer 走域名，取钥仍用 `http://keycloak:8080` |
| 已有 Keycloak 数据卷自动改 client | `--import-realm` 通常不覆盖已有 realm；要 fresh volume 或手工改 `skytrace-web` |
| 打生产 `v1.2.2` | 别的事 |

本地默认仍是 `http://localhost:8888` 和 `http://127.0.0.1:8888`。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `deploy/keycloak/skytrace-realm.json` 与 `.local.json` | redirect/webOrigin 改为 `${SKYTRACE_WEB_ORIGIN}` 占位 |
| `deploy/docker-compose.yml` | 本地 Keycloak 注入 localhost 默认 origin |
| `deploy/docker-compose.staging.yml` | 从 `SKYTRACE_DOMAIN` 覆盖 Keycloak/CORS/issuer |
| `deploy/docker-compose.production.yml` | 同样注入 web origin |
| `scripts/deploy-staging.sh` / `deploy-production.sh` | 拒绝空值、URL、通配和 localhost |
| `scripts/ci/assert_staging_oidc_urls.py` | CI 断言 overlay 与 compose config |
| 本文 + 审计索引 | 挂链接 |

---

## 5. 怎么确认

```bash
python3 scripts/keycloak/assert_realm_split.py
python3 scripts/ci/assert_staging_oidc_urls.py
```

有 Docker 时，第二条会用 `SKYTRACE_DOMAIN=oidc.test.example` 跑 staging `compose config`，确认展开成 `https://oidc.test.example`。

已部署环境：fresh Keycloak 卷才会导入新 redirect。旧卷要在 Admin Console 把 `skytrace-web` 的 Valid redirect URIs / Web origins 改成 `https://$SKYTRACE_DOMAIN/*`。

---

## 6. 再下一刀

Java/Node 上传（RB-12 尾巴）。`h2`、整栈回滚、Flyway 空库、detection 幂等仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.13`**（不要打在本分支上，不要打生产 `v1.2.2`）。
