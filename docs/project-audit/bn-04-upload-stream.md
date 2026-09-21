# BN-04（上传链去掉整文件内存复制）实施说明

适用分支：`fix/upload-chain-no-buffer-copy`  
只修 **Node BFF 转发给 Java 的 multipart**，以及 **Java 把知识库/视觉文件转给 AI**：不再 `Uint8Array` 再包一层，不再 `MultipartFile.getBytes()` 整份进堆。  
**不要**改 multer 落盘、证据衍生 `readAllBytes`、PDF 进程池、Flyway 空库、detection 幂等、整栈回滚、打生产 `v1.2.2`。

旧编号：BN-04 / RB-12 内存尾巴。上一刀是知识库 PDF 页数/超时（`#184`）和 `v1.2.2-rc.18`。magic-byte 本身见 [rb-12-java-node-upload-magic.md](rb-12-java-node-upload-magic.md)。

---

## 1. 一句话

50 MB 视频在 BFF 会再拷成 `Uint8Array` + `Blob`；Java 转发 AI 时 `getBytes()` 再整份进堆。证据入库已经按流写 MinIO，这条链还在复制。

本分支 Node 用同一段 ArrayBuffer 的 `Uint8Array` 视图构造 `Blob`（不再 `new Uint8Array(buffer)` 整份拷贝）；Java 先读 512 字节做 magic，再按 `InputStream` 转给 AI。重试时重新打开 multipart 流，不复用已读完的流。

---

## 2. 现在怎么坏的

```text
multer memory Buffer
        │
        ▼
new Uint8Array(file.buffer) + new Blob(...)     ← 多两份
        │
        ▼
Java MultipartFile.getBytes()                   ← 再整份进堆
        │
        ▼
ByteArrayResource 转给 AI
```

审计点：`java-client.service.ts` 的 `Uint8Array`/`Blob`，`AiVisionClient` / `AiKnowledgeClient` 的 `getBytes()`。证据 `store()` 已经流式，不在本刀范围。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| multer 改 diskStorage | 要管临时文件生命周期，独立验证 |
| 证据衍生 `readAllBytes` / 像素预算 | JV-08 其余项 |
| 可终止 PDF 进程池 | 另一刀 |
| Flyway 空库、detection 幂等、整栈回滚 | 各自独立 |
| 打生产 `v1.2.2` | 别的事 |

Node 仍用 multer 内存；`Blob` 构造仍会拷一次给 FormData。本刀去掉点名的额外 `Uint8Array` 和 Java `getBytes()`。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-java/.../UploadForwarding.java` | `sniff` 512 字节；`streamedBody` 用 `InputStreamResource` |
| `AiVisionClient` / `AiKnowledgeClient` | 先 sniff，再在每次 AI 调用时打开流 |
| `backend-node/.../upload-magic.ts` | `blobFromUpload`，不再 `new Uint8Array(buffer)` |
| `java-client.service.ts` | `postMultipart` 走 `blobFromUpload` |
| 对应 tests + 本文 + 审计索引 + 发版说明 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd backend-java && mvn -o -Dtest=UploadForwardingTest,UploadMagicTest test
cd backend-node && npm test
```

- 大于 sniff 窗口的 JPEG，嗅探后重放的流仍是完整原文
- HTML 冒充知识库 PDF 仍在读完全文前失败
- Node `blob.size` 等于原 Buffer 长度

---

## 6. 再下一刀

可终止 PDF 解析进程已单独实施，见 [ai-01-pdf-killable-process.md](ai-01-pdf-killable-process.md)。multer 落盘见 [bn-04-multer-disk.md](bn-04-multer-disk.md)。Flyway 空库、detection 幂等、整栈回滚、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.19`**（不要打在本分支上，不要打生产 `v1.2.2`）。
