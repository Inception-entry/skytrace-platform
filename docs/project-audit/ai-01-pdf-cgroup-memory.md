# AI-01（PDF 解析 cgroup 内存上限）实施说明

适用分支：`fix/ai-pdf-cgroup-memory`  
只给 **知识库 PDF 解析子进程** 套 cgroup v2 `memory.max`。超限被 OOM kill 时返回 400「文档解析超出内存上限」，不要把整个 AI worker 撑死。  
**不要**做 Flyway 空库、detection 幂等、整栈回滚、Admin 头像落盘、vitest mocker、打生产 `v1.2.2`。

旧编号：AI-01 尾巴（可终止进程之后的内存隔离）。上一刀是 multer 落盘（`#188`）和 `v1.2.2-rc.21`。可终止进程见 [ai-01-pdf-killable-process.md](ai-01-pdf-killable-process.md)。

---

## 1. 一句话

超时杀掉解析进程能收回 CPU，拦不住畸形 PDF 把 RSS 打满。生产 `backend-ai` 容器只有 1536 MiB，解析和 YOLO 挤在一起。

本分支给 spawn 子进程建 `skytrace-pdf-<pid>` cgroup，写入 `memory.max`（默认 256 MiB）和 `memory.swap.max=0`。写不了 cgroup 的环境（本地 pytest）跳过限制，解析照跑。

---

## 2. 现在怎么坏的

```text
spawn 解析子进程
        │
        ▼
pypdf 异常 /ToUnicode、font widths
        │
        ▼
RSS 涨到把 AI 容器 OOM
FastAPI / YOLO 一起没
```

审计原文在 [05-ai-service.md](05-ai-service.md) 第 2 节：独立 process 要有 wall-time **和** 内存限制。wall-time 已做。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| Markdown/TXT 也进 cgroup | 文本解码不会把 RSS 打满 |
| 改生产容器 mem_limit | overlay 已有 1536m，不是这一刀 |
| Flyway、detection 幂等、整栈回滚 | 各自独立 |
| RLIMIT_AS | 64 位 CPython 虚拟地址太大，正常 PDF 也会被误杀 |
| 打生产 `v1.2.2` | 别的事 |

默认 `AI_KNOWLEDGE_PARSE_MEMORY_BYTES=268435456`。`0` 关闭。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-ai/app/cgroup_memory.py` | `attach_pid` / `release_cgroup` / `MemoryLimitError` |
| `backend-ai/app/process_timeout.py` | 子进程 attach；SIGKILL+limit 映射成 MemoryLimitError |
| `backend-ai/app/knowledge_base.py` | 透出 400「文档解析超出内存上限」 |
| `backend-ai/app/config.py` | `knowledge_parse_memory_bytes` |
| `deploy/docker-compose.yml`、`deploy/.env.example` | 透出字节上限 |
| 对应 tests + 本文 + 审计索引 + 发版说明 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd backend-ai && uv run pytest tests/test_knowledge_base.py tests/test_cgroup_memory.py -v
```

- 假 cgroup 目录会写下 `memory.max` 和 `cgroup.procs`
- 子进程 SIGKILL 且启用了内存上限 → `MemoryLimitError` → 400
- 卡住的 worker 超时后仍会退出（原测试仍绿）
- 没有 memory controller 的目录会跳过 attach

---

## 6. 再下一刀

Flyway 空库已单独实施，见 [jv-01-flyway-empty-schema.md](jv-01-flyway-empty-schema.md)。下一刀是 detection 幂等。整栈回滚、Admin 头像落盘、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.22`**（不要打在本分支上，不要打生产 `v1.2.2`）。
