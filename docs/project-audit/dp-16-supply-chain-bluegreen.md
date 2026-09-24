# 基础镜像 digest、SBOM、非 root、入口蓝绿

适用分支：`feat/deploy-supply-chain-bluegreen`

第三方 Compose 镜像改成 `tag@sha256`。Temporal 从 `latest` 固定到 `1.28.1` / UI `2.39.0`。应用镜像仍由现有 digest overlay 钉死。Publish 在 digest manifest 之后用 Syft 产出七份 CycloneDX，跟 manifest 一起上传。

Java、Node、AI、Admin 运行用户是 `10001`。两个 Nginx 改听 `8080`，以 `nginx` 用户启动。宿主机端口仍是 `8888` / `8889`。Caddy 默认读 `deploy/caddy/upstream.caddy`，指向 `frontend:8080`。

`scripts/deploy-bluegreen.sh` 另起 `frontend-blue` 或 `frontend-green`，健康检查通过才改 upstream 并 reload Caddy。滚动部署脚本不会自动调用它。数据库和 API 容器名不变。
