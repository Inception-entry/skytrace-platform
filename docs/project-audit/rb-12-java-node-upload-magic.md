# RB-12（Java/Node 上传 magic-byte）实施说明

适用分支：`fix/java-node-upload-magic-bytes`  
只修 **Java 证据入库、知识库转发、视觉转发** 和 **Node BFF 对应入口**：按文件头识别类型，对象名/转发名用规范扩展名。  
**不要**做 PDF 页数、h2、整栈回滚、流式去内存复制、ZIP 全量消毒、打生产 `v1.2.2`。

旧编号：RB-12（本刀覆盖 Admin 头像之后剩下的 Java/Node 公网入口）。上一刀是发版说明同步（`#180`）和 `v1.2.2-rc.14`。

---

## 1. 一句话

证据、知识库、视觉上传只看客户端 MIME 和原始文件名。把 HTML 标成 `image/jpeg`、名叫 `x.php.jpg` 就能进 MinIO 或被转给 AI。

本分支只认 magic byte。证据对象键是 `{task|unassigned}/{uuid}.jpg|png|webp|mp4|webm`。知识库转发给 AI 的文件名是 `document.pdf|md|txt`。视觉转发用 `frame.*` / `clip.*`。

---

## 2. 现在怎么坏的

```text
mimetype=image/jpeg + 内容是 HTML     → EvidenceStorageService 写入证据桶
originalname=x.php.jpg                 → 对象键带着 .php.jpg
知识库 / 视觉                          → Java 把客户端 Content-Type 原样转给 AI
Node BFF                               → 只看 multer，不看文件头
归档 ZIP                               → entry 后缀取自原始文件名
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| PDF 页数/chunk 限额 | 另一刀 |
| 多层 Buffer 改成流式 | BN-04 内存尾巴，独立验证 |
| 完整 Zip Slip / Content-Disposition | JV-07 其余项 |
| `h2`、整栈回滚、打生产 `v1.2.2` | 别的事 |

知识库仍允许 Markdown/TXT（前端本来就收这三种）；这两类没有稳定 magic，只拒绝 HTML/NUL，并要求扩展名是 `.md` / `.markdown` / `.txt`。PDF 必须是 `%PDF`。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-java/.../upload/UploadMagic.java` | jpeg/png/webp/mp4/webm/pdf 白名单 |
| `EvidenceStorageService.store` | 先嗅探再 putObject；扩展名不跟 originalFilename |
| `AiKnowledgeClient` / `AiVisionClient` | 转发前嗅探；规范文件名和 Content-Type |
| `EvidenceManifestService` | ZIP 路径用入库 contentType |
| `backend-node/src/common/upload-magic.ts` | BFF 同样白名单 |
| evidence/knowledge/alarm controllers | 上传前 inspect |
| 对应单测 + 本文 + 审计索引 | 防回归 |

---

## 5. 怎么确认

```bash
cd backend-java && mvn -q -Dtest=UploadMagicTest,EvidenceStorageServiceTest,EvidenceManifestServiceTest test
cd backend-node && npm test
```

- JPEG/PNG/WEBP/MP4/WEBM 头能过，证据对象键扩展名不跟 `x.php.jpg`
- HTML / 空字节 400，且不 putObject
- 知识库：`%PDF` 能过；HTML 叫 `.pdf` 失败；正常 `.md` 能过
- 视觉：jpeg 不能当视频；mp4 `ftyp` 能过

负向（需本地打接口）：用 HTML 改扩展名为 `.jpg` 调 `POST /api/evidence` 必须 400。

---

## 6. 再下一刀

`h2` advisory 已单独实施，见 [ai-16-h2-upgrade.md](ai-16-h2-upgrade.md)。PDF 页数/超时见 [ai-01-pdf-parse-bounds.md](ai-01-pdf-parse-bounds.md)。上传流转发见 [bn-04-upload-stream.md](bn-04-upload-stream.md)。Flyway 空库、detection 幂等、整栈回滚仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.15`**（不要打在本分支上，不要打生产 `v1.2.2`）。
