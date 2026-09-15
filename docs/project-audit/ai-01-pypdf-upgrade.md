# RB-07 / AI-01 升级 pypdf：实施说明

适用分支：`fix/pypdf-advisory`  
只升 **AI 服务锁定的 `pypdf`**，让知识库 PDF 解析落到已修复的 6.15.0+。  
**不要**升 `h2`、不要加页数/chunk/超时限额、不要把解析搬出事件循环、不要改 Keycloak。本 PR 关掉两个已知 advisory，不代表 PDF 资源治理完成。

旧编号：RB-07、AI-01。路线图 Wave 1C 第 1 条（仅 pypdf）。上一刀是证据 Instant（`#162`）。

---

## 1. 一句话

知识库上传会用 `PdfReader` 抽文本。锁文件钉在 `pypdf 6.14.2`，该版本有两个恶意 PDF 资源耗尽漏洞（`/ToUnicode`、font widths），修复版本都是 `6.15.0`。`pyproject.toml` 写的是 `>=6,<7`，所以 6.14.2 一直合法。

本分支把下限改成 `>=6.15.0,<7`，并重新锁定。当前 `uv.lock` 解析到 `6.18.1`（仍在 6.x，含 6.15.0 的两处修复）。

---

## 2. 现在怎么坏的

```text
POST 知识库上传 PDF
        │
        ▼
KnowledgeBase.import_document
  PdfReader(BytesIO(content)).pages[].extract_text()
        │
        ▼
pypdf 6.14.2
  异常 /ToUnicode 或 font widths → 大内存 / 长时间运行
```

advisory：`GHSA-fp3f-mc75-235c`、`GHSA-fwg2-594c-jp42`。修复版本均为 `pypdf 6.15.0`。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| 升 `h2` | 另一条 advisory，适用性取决于是否暴露 HTTP/2，独立 PR |
| 页数 / chunk / 超时 / `to_thread` | 路线图 Wave 1C 后续项；升级不能冒充治理完成 |
| `uv lock` 无范围升级、`npm audit fix --force` | 只动 pypdf |
| 修 Keycloak 开发用户、AUTH-004 | 别的 PR |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `backend-ai/pyproject.toml` | `pypdf>=6.15.0,<7` |
| `backend-ai/uv.lock` | `uv lock --upgrade-package pypdf` |
| `backend-ai/tests/test_knowledge_base.py` | 锁定版本 ≥ 6.15.0；空白 PDF / 损坏 PDF 仍走现有错误路径 |
| 本文 + 审计 README / `01` / `05` / `10` | 挂索引 |

`knowledge_base.py` 解析逻辑本刀不动。

---

## 5. 怎么确认

```bash
cd backend-ai
uv lock --check
uv run pytest tests/test_knowledge_base.py -v
```

- `uv.lock` 里 `pypdf` 版本 ≥ `6.15.0`（当前锁定 `6.18.1`）
- 锁文件不再出现 `6.14.2`
- 现有知识库测试仍绿

---

## 6. 再下一刀

RB-04：生产 Keycloak realm 去掉三个开发账号。AUTH-004 仍是 P1。`h2` 和 PDF 解析限额另开 PR。
