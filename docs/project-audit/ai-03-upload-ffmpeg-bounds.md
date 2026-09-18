# AI-03 / AI-05 / RB-14（AI 有界读入与 FFmpeg 超时）实施说明

适用分支：`fix/ai-upload-ffmpeg-bounds`  
只修 **AI 视觉图片/视频上传读入** 和 **FFmpeg 抽帧超时**：先按上限读 `max+1` 字节，超限 413；FFmpeg 加 timeout、`-nostdin`、协议白名单，失败/超时不把 stderr 回给客户端。`maxFrames` 上限 30。  
**不要**做像素炸弹、Java/Node 上传、PDF 页数、h2、OIDC、版本号。

旧编号：AI-03、AI-05、RB-14。上一刀是 Node JWKS kid 冷却（`#174`）和 `v1.2.2-rc.10`。

---

## 1. 一句话

图片/视频 `file.read()` 整文件进内存后再看大小。FFmpeg `subprocess.run` 没有 timeout，stderr 原文会回到 API。

本分支有界读入，抽帧必须在超时内结束，客户端只看到固定中文错误。

---

## 2. 现在怎么坏的

```text
超大图/视频          → 先完整读入，再 413
ffmpeg 卡住           → worker 一直占着
-i 未限制协议         → 可能出文件路径以外的输入
stderr 回给调用方     → 路径/内部细节泄漏
maxFrames 无上限      → 抽帧放大
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 像素炸弹 / Pillow verify | AI-04，独立 PR |
| Java / Node 上传 magic-byte | 各自入口 |
| 视频落受限临时盘流式写入 | 下一截，本刀仍是内存上限 |
| h2、OIDC、打生产 `v1.2.2` | 别的事 |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-ai/app/uploads.py` | `read_upload_capped` |
| `backend-ai/app/main.py` | 图片/视频走有界读；`maxFrames` Query 上限 |
| `backend-ai/app/config.py` | 视频上限、FFmpeg timeout |
| `backend-ai/app/vision/video_frames.py` | timeout、`-nostdin`、协议白名单、错误脱敏 |
| `backend-ai/app/vision/analyze.py` | 把 timeout 传给抽帧 |
| 对应 tests + 本文 + 审计索引 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd backend-ai && uv run pytest tests/test_uploads.py tests/test_video_frames.py tests/test_vision_analyze.py -v
```

- 空文件 / 超限在读满之前就被拒绝
- FFmpeg 命令含 `-nostdin` 和 `protocol_whitelist`
- timeout 抛「视频处理超时」，stderr 不进异常文案
- `maxFrames` 超过 30 失败

---

## 6. 再下一刀

AI-04：图片像素炸弹已单独实施，见 [ai-04-image-pixel-budget.md](ai-04-image-pixel-budget.md)。下一刀是 RB-15（真实域名 OIDC）。Java/Node 上传、`h2`、整栈回滚仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.11`**（不要打在本分支上，不要打生产 `v1.2.2`）。
