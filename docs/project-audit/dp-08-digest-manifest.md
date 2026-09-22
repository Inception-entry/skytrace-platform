# DP-08（OCI digest manifest）实施说明

适用分支：`fix/v122-remaining`  
只让 **Publish 产出七个应用镜像的 digest manifest，预发/生产滚动按 digest 钉镜像**。`IMAGE_TAG=main-<sha>` 仍是人类检索和校验键，部署 overlay 用 `@sha256:...`。  
**不要**做蓝绿 / Caddy、改 Compose overlay 的 `${IMAGE_TAG:-latest}` 默认值、打生产 `v1.2.2`。

旧编号：DP-08 更强方案。拒绝 `latest` 见 [dp-08-immutable-image-tag.md](dp-08-immutable-image-tag.md)。整次回滚见 [rb-17-whole-deploy-rollback.md](rb-17-whole-deploy-rollback.md)。

---

## 1. 一句话

七个应用镜像不是一个原子对象。只记 `IMAGE_TAG` 时，tag 被重打后 `.current-image-tag` 回滚会对到别的内容。

本分支：Publish 在全部 push 成功后 `imagetools inspect` 写出 `release-manifest.json` 工件。部署时生成 compose overlay，把七个服务的 `image` 写成 `registry/service@sha256:...`。成功后同时写 `.current-image-tag` 和 `.current-release-manifest`。回滚优先用上一份 digest overlay。

---

## 2. 现在怎么坏的

```text
compose image: ${REGISTRY}/gateway:${IMAGE_TAG}
        │
        ▼
.current-image-tag=main-abc1234
        │
        ▼
main-abc1234 被重打 → 回滚内容漂移
```

审计原文在 [06-deployment-security-ci.md](06-deployment-security-ci.md) DP-08。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 蓝绿 / Caddy 切流量 | 1.3.0 |
| 改 staging overlay 的 `${IMAGE_TAG:-latest}` | 本地 compose 仍可能不带 IMAGE_TAG；digest overlay 后置覆盖 |
| 基础镜像（MinIO/Temporal）钉 digest | DP-16，另开 |
| 打生产 `v1.2.2` | 别的事 |

无新 Flyway。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `scripts/release_manifest.py` | validate / inspect / overlay |
| `scripts/deploy-production.sh` / `deploy-staging.sh` | 生成 digest overlay；成功写 `.current-release-manifest` |
| `.github/workflows/publish.yml` | `digest-manifest` job 上传工件 |
| `scripts/ci/assert_release_manifest.py` | 非法 tag 拒绝；overlay 必须是 digest |
| 对应本文 + 发版说明 | CI 跑断言 |

生产机也可以不带 `RELEASE_MANIFEST`：脚本对当前 `IMAGE_TAG` 现查 digest。带文件时必须 `manifest.tag == IMAGE_TAG`。

---

## 5. 怎么确认

```bash
python3 scripts/ci/assert_release_manifest.py
python3 scripts/ci/assert_production_deploy_rollback.py
python3 scripts/ci/assert_immutable_image_tag.py
```

- `latest` / 缺服务 / 非 sha256 的 digest → exit 2
- overlay 七个服务都是 `@sha256:...`，没有 `:latest`
- 生产第 N 个服务失败后仍逆序回滚，并继续写/保留 `.current-release-manifest`

---

## 6. 再下一刀

蓝绿 / Caddy、基础镜像 digest、SBOM 签名仍是 1.3.0。合入 `main` 后打 **`v1.2.2-rc.30`**。
