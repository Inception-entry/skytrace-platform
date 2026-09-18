# AS-09 / RB-12（Admin 头像 magic-byte）实施说明

适用分支：`fix/admin-avatar-magic-bytes`  
只修 **Admin 头像上传**：按文件头识别 jpeg/png/gif/webp，对象名用规范扩展名，空文件/超 2MB 拒绝；桶初始化用共享 Promise。管理台 `/files/` 加 `nosniff`。  
**不要**做 AI/Java 上传、private bucket + 签名 URL、像素炸弹、ZIP、FFmpeg、JWKS、h2、版本号。

旧编号：AS-09、RB-12（本刀只覆盖 Admin 头像这一条公网入口）。上一刀是登录失败路径对齐（`#172`）和 `v1.2.2-rc.8`。

---

## 1. 一句话

头像只看客户端 `mimetype` 和原始文件名。把 HTML 标成 `image/jpeg`、名叫 `avatar.php.jpg` 就能进公开桶，再被浏览器当图片/脚本解释。

本分支只认 magic byte，对象名是 `{userId}/{uuid}.jpg|png|gif|webp`，Content-Type 跟检测结果走。

---

## 2. 现在怎么坏的

```text
mimetype=image/jpeg + 内容是 HTML     → 写入公开 GetObject
originalname=x.php.jpg                 → 对象名带着 .php.jpg
空文件 / 只看 multer size              → 没有二次检查 buffer
并发首次上传                           → 各自 makeBucket，异常被吞
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 改成 private bucket + 签名 URL | 会打断现有 `/files/` 契约，另开一刀 |
| AI / Java / Node 证据上传 | RB-12 其余入口，独立验证 |
| 像素/解压炸弹、FFmpeg | RB-14 |
| JWKS、h2、打生产 `v1.2.2` | 别的事 |

公开读仍然在。补偿是 magic-byte + `nosniff`，不是改 ACL。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-service/src/upload/avatar-bytes.ts` | magic 白名单、空/大小、规范对象名 |
| `admin-service/src/upload/upload.service.ts` | 用检测结果写 Content-Type；桶 init 共享 Promise |
| `admin-service/src/upload/upload.controller.ts` | 大小上限与常量对齐 |
| `admin-frontend/nginx.conf` | `/files/` 加 `X-Content-Type-Options: nosniff` |
| 对应 `*.spec.ts` + 本文 + 审计索引 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd admin-service && npm test -- src/upload/avatar-bytes.spec.ts src/upload/upload.service.spec.ts
```

- JPEG/PNG/GIF/WEBP 头能过，扩展名不跟 originalname
- HTML / SVG / 空 / 超 2MB 400
- 客户端声称 jpeg 但内容是 HTML：不 putObject
- 并发首次建桶只 `makeBucket` 一次

负向（需本地打接口）：用 HTML 文件改扩展名为 `.jpg` 调 `POST /admin-api/upload/avatar` 必须 400。

---

## 6. 再下一刀

RB-13：JWKS `kid` 冷却与未知 kid 负缓存。AI/Java 上传、private bucket、`h2`、PDF 限额仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.9`**（不要打在本分支上，不要打生产 `v1.2.2`）。
