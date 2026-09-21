# AI-04（图片像素炸弹）实施说明

适用分支：`fix/ai-image-pixel-budget`  
只修 **AI 视觉解码前的像素预算**：先读 header 检查边长/总像素，再 `verify()`，超限或无法解析抛 `InvalidImage`，接口返回 400 而不是 502。YOLO 路径同样走有界 `decode_rgb_image`。  
**不要**做 Java/Node 上传、PDF 页数、h2、OIDC、版本号。

旧编号：AI-04。上一刀是有界读入/FFmpeg（`#175`）和 `v1.2.2-rc.11`。

---

## 1. 一句话

YOLO 路径 `Image.open(...).convert("RGB")` 在看宽高之前就解码。一张声明超大分辨率的图可以把 worker 打满内存。

本分支先看 header 像素预算，超限直接拒绝。无效图 400。

---

## 2. 现在怎么坏的

```text
IHDR 写 20000×20000 的 PNG   → convert 才炸内存
坏图 / 非图片                 → 被 analyze 包成 retryable 502
```

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| Java / Node 上传 | 各自入口 |
| PDF 页数/chunk | 另一刀 |
| h2、OIDC、打生产 `v1.2.2` | 别的事 |

默认上限：单边 4096，总像素 8,388,608（可用 `AI_VISION_MAX_IMAGE_SIDE` / `AI_VISION_MAX_IMAGE_PIXELS` 调）。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-ai/app/vision/image_bounds.py` | header 预算 + `decode_rgb_image` |
| `backend-ai/app/vision/analyze.py` | 推理前先断言预算 |
| `backend-ai/app/vision/detector.py` | YOLO 不再裸 `convert` |
| `backend-ai/app/main.py` | `InvalidImage` → 400 |
| `backend-ai/app/config.py` + `pyproject.toml` | 上限配置；Pillow 提升为直接依赖 |
| tests + 本文 + 审计索引 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd backend-ai && .venv/bin/python -m pytest tests/test_image_bounds.py tests/test_vision_analyze.py tests/test_vision.py -q
```

- 小 PNG 能过
- 非图片 / 空字节失败
- IHDR 超大分辨率在 decode 前失败
- `analyze_image` 对坏图抛 `InvalidImage`

---

## 6. 再下一刀

RB-15：真实域名 OIDC 已单独实施，见 [rb-15-oidc-public-domain.md](rb-15-oidc-public-domain.md)。下一刀是 Java/Node 上传。`h2`、整栈回滚仍各自独立。

合入 `main` 后若要再发候选，打 **`v1.2.2-rc.12`**（不要打在本分支上，不要打生产 `v1.2.2`）。
