# AI-01（PDF 页数 / 切片 / 解析超时）实施说明

适用分支：`fix/ai-pdf-page-timeout`  
只给 **知识库解析** 加页数、提取字数、切片数和 wall-time 上限；解析放到 `asyncio.to_thread`，超时返回 400。  
**不要**做进程池强杀、上传链多层内存复制、Flyway 空库、detection 幂等、整栈回滚、vitest mocker、打生产 `v1.2.2`。

旧编号：AI-01 尾巴（Wave 1C 第 3 条 PDF 部分）。上一刀是 Admin 生产 npm（`#183`）和 `v1.2.2-rc.17`。pypdf 升级本身见 [ai-01-pypdf-upgrade.md](ai-01-pypdf-upgrade.md)。

---

## 1. 一句话

知识库在请求协程里整本 `PdfReader.extract_text()`，没有页数、字数、切片上限，也没有超时。恶意或超大 PDF 能把 AI worker 卡住。

本分支先数页再抽文本，超页/超字/超切片直接 400；解析进线程并 `wait_for`，超时也是 400。线程超时只停止等待，不会杀掉正在跑的 CPU 解析。

---

## 2. 现在怎么坏的

```text
POST 知识库上传 PDF
        │
        ▼
KnowledgeBase.import_document
  事件循环里 PdfReader(...).pages[].extract_text()
        │
        ▼
超页 / 畸形字体 / 超长文本
  worker 长时间占用，后面的上传排队
```

审计草稿在 [05-ai-service.md](05-ai-service.md) 第 2 节。pypdf advisory 已升到 6.18.1；本刀补资源边界。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 可终止进程池 / cgroup 内存上限 | 线程 timeout 杀不掉 CPU；独立 PR |
| Node/Java 上传链去掉多层 Buffer 复制 | 另一刀 |
| Flyway 空库、detection 幂等、整栈回滚 | 各自独立 |
| 打生产 `v1.2.2` | 别的事 |

默认上限：80 页、40 万提取字符、400 切片、20 秒。运维可用 `AI_KNOWLEDGE_*` 调整。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-ai/app/config.py` | `knowledge_max_pages` / `_chunks` / `_extract_chars` / `_parse_timeout_seconds` |
| `backend-ai/app/knowledge_base.py` | 解析进线程；页数/字数/切片检查；超时转 ValueError |
| `backend-ai/tests/test_knowledge_base.py` | 超页、超切片、超字、超时 |
| `deploy/docker-compose.yml`、`deploy/.env.example` | 透出新环境变量 |
| 本文 + 审计索引 + 发版说明 | 挂链接 |

---

## 5. 怎么确认

```bash
cd backend-ai && uv run pytest tests/test_knowledge_base.py -v
```

- 3 页空白 PDF 在 `knowledge_max_pages=2` 时失败（「页数超过限制」），不是「没有可提取的文字」
- 长 Markdown 超切片 / 超字数分别失败
- 解析卡住超过 timeout 失败（「文档解析超时」）
- 空白 PDF、损坏 PDF、正常 Markdown 入库路径不变

---

## 6. 再下一刀

上传链多层内存复制。Flyway 空库、detection 幂等、整栈回滚、vitest mocker、可终止 PDF 进程池仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.18`**（不要打在本分支上，不要打生产 `v1.2.2`）。
