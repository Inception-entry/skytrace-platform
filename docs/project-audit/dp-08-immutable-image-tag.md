# DP-08（生产/预发拒绝可变 `latest`）实施说明

适用分支：`fix/immutable-image-tag`  
只让 **预发/生产部署脚本和生产 workflow 拒绝不是 `main-<git-sha>` 的 `IMAGE_TAG`**。`latest` 不再能进滚动发布。  
**不要**做 digest manifest、蓝绿 / Caddy、改 Compose overlay 默认值、Evidence MinIO/workflow outbox、Admin 头像落盘、vitest mocker、打生产 `v1.2.2`。不要改已发布的 Flyway V2–V22。

旧编号：DP-08。上一刀是整次部署回滚（`#208`）和 `v1.2.2-rc.28`。

---

## 1. 一句话

生产 workflow 默认 `latest`；脚本不校验 tag。可变引用无法证明部署内容，`.current-image-tag` 回滚也不可靠。

本分支：`IMAGE_TAG` 必须匹配 `^main-[0-9a-f]{7,40}$`。生产 workflow 去掉 `latest` 默认值，输入不合规则直接失败。

---

## 2. 现在怎么坏的

```text
workflow_dispatch default: latest
        │
        ▼
IMAGE_TAG=latest compose up
        │
        ▼
.current-image-tag=latest
        │
        ▼
回滚仍指向可变 latest，内容可能已变
```

审计原文在 [06-deployment-security-ci.md](06-deployment-security-ci.md) DP-08。Publish 流水线已经打 `main-<short-sha>`。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 按 OCI digest 部署 / release manifest | DP-08 更强方案 |
| 改 `docker-compose.staging.yml` 的 `${IMAGE_TAG:-latest}` | 本地 compose 仍可能不带 IMAGE_TAG |
| 蓝绿 / Caddy | 别的事 |
| Evidence MinIO/workflow outbox | JV-04 其余项 |
| 打生产 `v1.2.2` | 别的事 |

无新 Flyway。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `scripts/deploy-production.sh` / `deploy-staging.sh` | 非 `main-<hex 7–40>` 则 exit 2 |
| `.github/workflows/deploy-production.yml` | 去掉 default `latest`；输入校验 |
| `scripts/ci/assert_immutable_image_tag.py` | `latest` / SemVer / 空值必须被拒 |
| 对应本文 + 发版说明 | CI 跑断言 |

回滚目标仍读 `.current-image-tag` 原文：若历史上写过 `latest`，unwind 仍用那个值，避免中途失败时再因旧 tag 不合规则而卡住。

---

## 5. 怎么确认

```bash
python3 scripts/ci/assert_immutable_image_tag.py
python3 scripts/ci/assert_production_deploy_rollback.py
```

- `IMAGE_TAG=latest` / `v1.2.2` / 空 → exit 2，不跑 compose
- `IMAGE_TAG=main-abc1234` 仍能走滚动与回滚断言
- 生产 workflow YAML 不再出现 `default: latest`

---

## 6. 再下一刀

Evidence MinIO/workflow outbox 已单独实施，见 [jv-04-evidence-outbox.md](jv-04-evidence-outbox.md)。digest manifest 见 [dp-08-digest-manifest.md](dp-08-digest-manifest.md)。Admin 头像落盘见 [as-09-admin-avatar-disk.md](as-09-admin-avatar-disk.md)。蓝绿仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.29`**（不要打在本分支上，不要打生产 `v1.2.2`）。
