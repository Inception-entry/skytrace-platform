# AI-16（升级 h2）实施说明

适用分支：`fix/ai-h2-advisory`  
只升 **AI 服务锁定的 `h2`**，让 `qdrant-client` → `httpx[http2]` 落到已修复的 4.4.1+。  
**不要**升 Admin npm、不要改 PDF 页数、不要动 Java `com.h2database`、不要打生产 `v1.2.2`。

旧编号：AI-16。上一刀是 Java/Node 上传 magic-byte（`#181`）和 `v1.2.2-rc.15`。

---

## 1. 一句话

锁文件钉在 `h2 4.3.0`。该版本接受重复 Host header；HTTP/2 降到 HTTP/1.1 时会变成 request-smuggling primitive。修复版本是 `4.4.1`。

本分支把 `h2>=4.4.1` 提成直接依赖下限（和 `anyio` 一样），并重新锁定。当前 `uv.lock` 解析到 `4.4.1`。

---

## 2. 现在怎么坏的

```text
qdrant-client
    └─ httpx[http2]
         └─ h2 4.3.0
              重复 Host header 仍合法
              降级成 HTTP/1.1 后变成两条 Host
```

advisory：`GHSA-6hr6-w5qg-qmwg` / `CVE-2026-71554` / `PYSEC-2026-3628`。修复版本 `h2 4.4.1`。

AI 进程当前主要当 HTTP/2 **客户端** 连 Qdrant，不是对外 HTTP/2 入口。适用性不如 pypdf 直接，但锁文件会被 Trivy 扫到，且修复无行为变更。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| Admin npm advisory | RB-16 其余项，独立验证 |
| PDF 页数 / chunk / 超时 | 另一刀 |
| 升 Java `com.h2database` | 不是这个 Python 包 |
| `uv lock` 无范围升级 | 只动 h2 |
| 打生产 `v1.2.2` | 别的事 |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-ai/pyproject.toml` | `h2>=4.4.1` |
| `backend-ai/uv.lock` | `uv lock --upgrade-package h2` |
| `backend-ai/tests/test_h2_advisory.py` | 安装版本 ≥ 4.4.1 |
| 本文 + 审计索引 + 发版说明 | 挂链接 |

---

## 5. 怎么确认

```bash
cd backend-ai
uv lock --check
uv run pytest tests/test_h2_advisory.py tests/test_knowledge_base.py -q
```

- `uv.lock` 里 `h2` 版本 ≥ `4.4.1`（当前锁定 `4.4.1`）
- 锁文件不再出现 `4.3.0`

---

## 6. 再下一刀

Admin npm advisory（RB-16 尾巴）。PDF 页数/超时、Flyway 空库、detection 幂等、整栈回滚仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.16`**（不要打在本分支上，不要打生产 `v1.2.2`）。
