# AS-09（Admin 头像 multer 落盘）实施说明

适用分支：`fix/v122-remaining`  
只把 **Admin 头像上传** 从 multer 内存改成受控临时文件：只读文件头做 magic，再按 `ReadStream` 写 MinIO，请求结束删临时文件。  
**不要**改 private bucket / 签名 URL、像素炸弹、打生产 `v1.2.2`。

旧编号：AS-09 尾巴（magic-byte 之后的 multer 内存）。上一刀 magic-byte 见 [as-09-admin-avatar-magic.md](as-09-admin-avatar-magic.md)。Node BFF 落盘见 [bn-04-multer-disk.md](bn-04-multer-disk.md)。

---

## 1. 一句话

头像上限 2MB，但 `FileInterceptor` 默认 `memoryStorage`，整份进堆再 `putObject(buffer)`。和证据链同一类问题，只是体积更小。

本分支 multer 写到 `AVATAR_TEMP_DIR`（默认 `os.tmpdir()/skytrace-admin-avatars`），文件名用 UUID。读写前丢掉调用方目录。magic 只读前 16 字节。MinIO 用 `createReadStream`。`finally` 里 `unlink`。

---

## 2. 现在怎么坏的

```text
multer memory Buffer     ← 整份进堆
        │
        ▼
inspectAvatarBuffer
        │
        ▼
putObject(buffer)        ← 再持有一份
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| private bucket + 签名 URL | 会打断 `/files/` 契约 |
| 像素/解压炸弹 | 另开一刀 |
| 打生产 `v1.2.2` | 别的事 |

崩溃残留不自动清，运维可扫 `AVATAR_TEMP_DIR`。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `admin-service/src/upload/avatar-disk.ts` | `diskStorage`、UUID 文件名、路径消毒、结束时 unlink |
| `avatar-bytes.ts` | `inspectAvatarMagic` 只看文件头 |
| `upload.controller.ts` / `upload.service.ts` | disk 选项 + stream putObject |
| `deploy/docker-compose.yml`、`deploy/.env.example` | `AVATAR_TEMP_DIR` |
| 对应 tests + 本文 + 发版说明 | 单测 |

---

## 5. 怎么确认

```bash
cd admin-service && npm test -- src/upload/avatar-disk.spec.ts src/upload/upload.service.spec.ts src/upload/avatar-bytes.spec.ts
```

- JPEG 头能过，对象名仍是 `{userId}/{uuid}.jpg`
- HTML 冒充 jpeg 仍 400，且临时文件被删
- 临时目录外的路径、非 UUID 文件名必须 400
- `putObject` 收到的是 stream，不是 Buffer

---

## 6. 再下一刀

与 Evidence outbox、vitest mocker、digest 同一次合入。合入 `main` 后打 **`v1.2.2-rc.30`**。
