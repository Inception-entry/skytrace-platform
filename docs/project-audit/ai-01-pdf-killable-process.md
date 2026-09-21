# AI-01（可终止 PDF 解析进程）实施说明

适用分支：`fix/ai-pdf-killable-process`  
只把 **知识库 PDF 解析** 从 `asyncio.to_thread` 换成可 `terminate`/`kill` 的 spawn 子进程。超时后子进程必须退出，不能继续占 CPU。  
**不要**做 cgroup 内存上限、multer 落盘、Flyway 空库、detection 幂等、整栈回滚、vitest mocker、打生产 `v1.2.2`。

旧编号：AI-01 尾巴（页数/超时之后的进程强杀）。上一刀是上传流转发（`#185`）和 `v1.2.2-rc.19`。页数/超时见 [ai-01-pdf-parse-bounds.md](ai-01-pdf-parse-bounds.md)。

---

## 1. 一句话

`wait_for(to_thread(PdfReader.extract_text))` 超时只停止等待，解析线程还在跑。畸形 PDF 仍能把 worker CPU 占满。

本分支 PDF 解析进 `spawn` 子进程。超时先 `terminate` 再 `kill`。Markdown/TXT 仍走线程。默认同时最多 2 个解析进程。

---

## 2. 现在怎么坏的

```text
wait_for(to_thread(_parse_document))
        │
        ▼
TimeoutError → 400「文档解析超时」
        │
        ▼
线程里的 pypdf 还在 extract_text()
  CPU 继续烧，后面的上传排队
```

审计原文在 [05-ai-service.md](05-ai-service.md) 第 2 节：线程 timeout 杀不掉 CPU 解析。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| cgroup 内存上限 | 编排/运维项，独立验证 |
| Markdown/TXT 也进子进程 | 文本解码不会把 CPU 卡死 |
| multer 落盘、Flyway、detection 幂等、整栈回滚 | 各自独立 |
| 打生产 `v1.2.2` | 别的事 |

默认超时仍是 20 秒；并发 `AI_KNOWLEDGE_PARSE_CONCURRENCY=2`。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-ai/app/process_timeout.py` | `run_in_killable_process`：Pipe + spawn + timeout kill |
| `backend-ai/app/knowledge_parse.py` | 抽出可 pickle 的 `parse_document` |
| `backend-ai/app/knowledge_base.py` | PDF 走可杀进程；MD/TXT 仍 `to_thread` |
| `backend-ai/app/config.py` | `knowledge_parse_concurrency` |
| `deploy/docker-compose.yml`、`deploy/.env.example` | 透出并发 |
| 对应 tests + 本文 + 审计索引 + 发版说明 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd backend-ai && uv run pytest tests/test_knowledge_base.py -v
```

- 卡住的 worker 在 timeout 后从 `multiprocessing.active_children()` 消失
- 空白 PDF、损坏 PDF、超页仍走原来的 400
- Markdown 超时测试仍绿（不进子进程）

---

## 6. 再下一刀

multer 落盘。Flyway 空库、detection 幂等、整栈回滚、cgroup 内存上限、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.20`**（不要打在本分支上，不要打生产 `v1.2.2`）。
