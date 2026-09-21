# RB-17 / DP-07（生产整次部署回滚）实施说明

适用分支：`fix/production-whole-deploy-rollback`  
只让 **生产滚动发布失败时，把本次已经升上去的服务逆序退回上一 `IMAGE_TAG`**。不要留下新旧混跑。  
**不要**做蓝绿 / Caddy 切流量、digest manifest、禁止 `latest`、Evidence MinIO/workflow outbox、Admin 头像落盘、vitest mocker、打生产 `v1.2.2`。不要改已发布的 Flyway V2–V22。

旧编号：RB-17、DP-07。上一刀是告警 outbox（`#207`）和 `v1.2.2-rc.27`。

---

## 1. 一句话

`deploy-production.sh` 按依赖顺序逐个 `compose up`。第 N 个服务挂了只回滚当前这一个，前面 N-1 个留在新 tag。

本分支：记下本次已更新的服务；compose 失败或健康检查失败时，失败服务 + 已更新服务按逆序回到 `.current-image-tag`。成功前不改写该文件。

---

## 2. 现在怎么坏的

```text
backend-ai / java / node 已是 main-new
        │
        ▼
gateway up 或 health 失败
        │
        ▼
只 rollback_service gateway
        │
        ▼
ai/java/node = 新 tag，其余 = 旧 tag
```

staging 的 `trap rollback` 是整 compose 一把退。生产滚动没有对等能力。

审计原文在 [06-deployment-security-ci.md](06-deployment-security-ci.md) DP-07 和 [01-release-blockers.md](01-release-blockers.md) RB-17。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 蓝绿 Compose / Caddy 切 upstream | 下一阶段，不是最低修复 |
| 拒绝 `latest`、按 digest 部署 | DP-08 |
| Flyway/Prisma 回滚门禁 | DP-09 |
| Evidence MinIO/workflow outbox | JV-04 其余项 |
| 打生产 `v1.2.2` | 别的事 |

无新 Flyway。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `scripts/deploy-production.sh` | `updated_services` + `rollback_release` 逆序回滚 |
| `scripts/ci/assert_production_deploy_rollback.py` | 假 docker/curl：gateway 失败时回滚顺序和 tag 文件 |
| 对应本文 + 发版说明 | CI 跑断言 |

没有上一 tag（首次部署）时无法 unwind，脚本失败并说明原因，不虚构回滚目标。

---

## 5. 怎么确认

```bash
python3 scripts/ci/assert_production_deploy_rollback.py
python3 scripts/ci/assert_staging_oidc_urls.py
```

- gateway 在新 tag 失败：回滚顺序 `gateway → node → java → ai`，`.current-image-tag` 仍是旧值
- 七个服务全绿才写入新 tag
- 无 `.current-image-tag` 时中途失败不得编造上一版本

---

## 6. 再下一刀

Evidence MinIO/workflow outbox，或 DP-08（生产拒绝 `latest`）。Admin 头像落盘、vitest mocker、蓝绿仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.28`**（不要打在本分支上，不要打生产 `v1.2.2`）。
