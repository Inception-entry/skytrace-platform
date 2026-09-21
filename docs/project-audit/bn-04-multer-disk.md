# BN-04（multer 落盘）实施说明

适用分支：`fix/node-multer-disk`  
只把 **Node BFF 证据 / 知识库 / 视觉上传** 从 multer 内存改成受控临时文件：只读文件头做 magic，再按 `ReadStream` 转给 Java，请求结束删临时文件。  
**不要**改 Admin 头像落盘、证据衍生 `readAllBytes`、cgroup 内存上限、Flyway 空库、detection 幂等、整栈回滚、vitest mocker、打生产 `v1.2.2`。

旧编号：BN-04 尾巴（流转发之后的 multer 内存）。上一刀是可终止 PDF 解析（`#186`）和 `v1.2.2-rc.20`。Java 流转发见 [bn-04-upload-stream.md](bn-04-upload-stream.md)。magic-byte 见 [rb-12-java-node-upload-magic.md](rb-12-java-node-upload-magic.md)。

---

## 1. 一句话

50 MB 视频会先整份进 multer `memoryStorage`，再拷进 `Blob`/`FormData`。生产 Node 只有 256 MiB，这一条就能把 BFF 顶满。

本分支 multer 写到 `UPLOAD_TEMP_DIR`（默认 `os.tmpdir()/skytrace-uploads`），文件名用 UUID。读写前用 `resolvedUploadPath` 丢掉调用方目录，只拼接临时目录 + UUID。magic 只读前 512 字节。转发给 Java 用 `form-data` + `createReadStream`。`finally` 里 `unlink`。

---

## 2. 现在怎么坏的

```text
multer memory Buffer          ← 50MB 进堆
        │
        ▼
Blob / FormData               ← 再拷一次
        │
        ▼
axios 整包 POST 给 Java
```

审计点：`FileInterceptor` 默认 memoryStorage，`java-client.service.ts` 的 `blobFromUpload`。Admin 头像仍是小图内存，不在本刀。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| Admin 头像改 diskStorage | 头像上限小，独立验证 |
| 证据衍生 `readAllBytes` / 像素预算 | JV-08 其余项 |
| cgroup 内存上限 | 编排/运维项 |
| Flyway 空库、detection 幂等、整栈回滚 | 各自独立 |
| 打生产 `v1.2.2` | 别的事 |

临时目录默认 `/tmp/skytrace-uploads`。崩溃残留不自动清，运维可扫这个目录。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-node/src/common/upload-disk.ts` | `diskStorage`、UUID 文件名、`resolvedUploadPath`、结束时 unlink |
| `backend-node/src/common/upload-magic.ts` | 仍只做 buffer magic；落盘嗅探改到 `upload-disk.ts` |
| `backend-node/src/common/java-client/java-client.service.ts` | `form-data` + `createReadStream` |
| evidence / knowledge / alarm 控制器 | `diskUploadOptions` + `withDiskUpload` |
| `deploy/docker-compose.yml`、`deploy/.env.example` | `UPLOAD_TEMP_DIR` |
| 对应 tests + 本文 + 审计索引 + 发版说明 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd backend-node && npm test
```

- 大于 sniff 窗口的 JPEG 仍识别为 jpeg，嗅探长度是 512
- 知识库 PDF 仍把文件名改成 `document.pdf`
- `withDiskUpload` 成功和失败都会删临时文件
- 临时目录外的路径、非 UUID 文件名必须 400
- HTML 冒充 jpeg/pdf 仍 400

---

## 6. 再下一刀

cgroup 内存上限已单独实施，见 [ai-01-pdf-cgroup-memory.md](ai-01-pdf-cgroup-memory.md)。Flyway 空库、detection 幂等、整栈回滚、Admin 头像落盘、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.21`**（不要打在本分支上，不要打生产 `v1.2.2`）。
