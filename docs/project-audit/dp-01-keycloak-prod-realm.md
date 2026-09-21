# RB-04 / DP-01 生产 Keycloak 不再导入开发账号：实施说明

适用分支：`fix/keycloak-prod-no-dev-users`  
只拆 **Keycloak realm 导入文件**：本地/CI 仍有三个 `@local.test` 用户，生产导入不再带这些账号和共享密码。  
**不要**改 redirect URI、不要停 `--import-realm`、不要迁 Admin 到 Keycloak BFF（AUTH-004 仍是 P1）、不要升级 h2。

改 JSON **不会**删掉已经写进生产 Keycloak 数据库的用户；已部署环境要运维在 Admin Console / kcadm 里盘点并禁用。

旧编号：RB-04、DP-01、AUTH-007。上一刀是 pypdf（`#163`）。

---

## 1. 一句话

`skytrace-realm.json` 里有 `skytrace-admin` / `operator` / `viewer`，密码都是 `${SKYTRACE_DEV_USER_PASSWORD}` 且 `temporary: false`。生产 overlay 继续 `start --import-realm`，并继承同一挂载，fresh 生产库会导入永久开发账号。

本分支：

1. `skytrace-realm.json` 只保留 `skytrace-service` 的 service account。
2. 三个开发用户挪到 `skytrace-realm.local.json`，给本地、CI、staging 测试用。
3. 生产 overlay 把导入文件换成不含开发用户的 JSON。
4. `sync-test-users.sh` 没有 `KEYCLOAK_ALLOW_TEST_USERS=true` 直接拒绝。

---

## 2. 现在怎么坏的

```text
docker compose ... -f docker-compose.production.yml
        │
        ▼
keycloak: start --import-realm
  挂载 deploy/keycloak/skytrace-realm.json
        │
        ▼
导入 skytrace-admin / operator / viewer
  同一共享密码，temporary=false
```

已有 realm 通常不会被后续 import 删用户，所以旧库里的开发账号还在，必须另做盘点。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 改 localhost redirect URI | DP-02 / RB-15，独立 P1。现已实施，见 [rb-15-oidc-public-domain.md](rb-15-oidc-public-domain.md) |
| 停 `--import-realm`、上 kcadm/Terraform 迁移 | 认证方案 2.2，不是这一刀 |
| 自动删除生产库里已有用户 | 要有权限的人盘点后手工禁用 |
| AUTH-004 Admin refresh、h2、PDF 限额 | 别的 PR |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `deploy/keycloak/skytrace-realm.json` | 去掉三个开发用户 |
| `deploy/keycloak/skytrace-realm.local.json` | 本地/CI 完整 realm（含三个用户） |
| `deploy/docker-compose.yml` | 挂载 `.local.json` |
| `deploy/docker-compose.production.yml` | 同一容器路径改挂生产 JSON |
| `scripts/keycloak/sync-test-users.sh` | 无显式允许则拒绝 |
| `scripts/skytrace.sh auth-users` | 本地调用时带 `KEYCLOAK_ALLOW_TEST_USERS=true` |
| `scripts/keycloak/assert_realm_split.py` | 生产无开发用户；两份 realm 除 users 外一致 |
| 本文 + 审计索引 | 挂链接 |

---

## 5. 怎么确认

```bash
python3 scripts/keycloak/assert_realm_split.py
KEYCLOAK_ALLOW_TEST_USERS=true scripts/keycloak/sync-test-users.sh  # 本地已起 Keycloak 时
```

负向：不设 `KEYCLOAK_ALLOW_TEST_USERS` 时 `sync-test-users.sh` 必须非 0 退出。

`docker compose ... -f docker-compose.production.yml config` 里，Keycloak 导入路径应是 `skytrace-realm.json`，不是 `.local.json`。

生产 **fresh** 卷不应再出现 `skytrace-admin@local.test`。已经跑过的库需要运维禁用这三个用户并视情况轮换 client secret。

---

## 6. 再下一刀

P0 代码项到此结束。下一刀是 P1：AUTH-004 / RB-08（Admin 前端 refresh 挂起），见 [rb-08-admin-refresh-hang.md](rb-08-admin-refresh-hang.md)。`h2`、PDF 解析限额、redirect URI 仍各自独立。
