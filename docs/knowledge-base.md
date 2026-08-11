# 无人机知识库与 RAG

## 调用链

```text
Vue /knowledge
  -> Nginx
  -> Spring Cloud Gateway（JWT、角色、限流）
  -> NestJS BFF（上传和响应转发）
  -> Spring Boot（业务 API 边界）
  -> FastAPI / LangChain
  -> Ollama nomic-embed-text + Qdrant
```

聊天分析仍由 Java 从 MySQL 读取真实巡检任务数据。FastAPI 使用用户原始问题检索 Qdrant，把命中的手册片段和 MySQL 任务上下文一起交给 `my-drone-expert`，并在回答末尾返回参考文档。Qdrant 不保存巡检任务业务数据。

`/chat` 页面通过 `POST /api/inspection-tasks/{taskCode}/analysis/stream`
建立 SSE 响应。FastAPI 依次发送 `meta`、`token`、`done` 事件；发生异常时
发送 `error`。Nginx、Gateway、Node 和 Java 均关闭响应缓冲或直接透传，
因此浏览器能逐段显示模型输出。
Java 会在完整回答写入 MySQL 后再转发 `done`；连接中断、模型错误或数据库
写入失败时不会保存成功记录。历史结果可通过
`GET /api/inspection-tasks/{taskCode}/analyses` 查询。

## 首次准备

安装本地嵌入模型：

```bash
ollama pull nomic-embed-text
```

确认 `deploy/.env` 至少包含：

```dotenv
AI_OLLAMA_MODEL=my-drone-expert
AI_OLLAMA_EMBEDDING_MODEL=nomic-embed-text
QDRANT_HOST_PORT=6333
AI_QDRANT_COLLECTION=skytrace_knowledge
```

重新构建受影响服务：

```bash
./scripts/skytrace.sh rebuild qdrant backend-ai backend-java backend-node gateway frontend
```

打开 `http://localhost:8888/knowledge`。`ADMIN` 可以上传和删除文档，`ADMIN`、`OPERATOR`、`VIEWER` 都可以列出文档和进行语义检索。

## 支持范围

- 文件类型：PDF、Markdown、TXT。
- 文本编码：Markdown 和 TXT 使用 UTF-8。
- 文件大小：默认最大 10 MB。
- 分段：默认 800 字符、120 字符重叠。
- 检索：默认返回 4 个片段，相似度阈值 0.25。
- 去重：文件内容的 SHA-256 作为文档 ID；重复上传相同内容会覆盖原向量。
- PDF：当前只提取文本层；扫描版 PDF 需要后续增加 OCR。

相关参数均可通过 `deploy/.env` 中的 `AI_KNOWLEDGE_*` 和 `AI_QDRANT_COLLECTION` 调整。

## 排错

查看服务状态和日志：

```bash
./scripts/skytrace.sh status
./scripts/skytrace.sh logs qdrant backend-ai
```

检查 AI 依赖：

```bash
curl "http://localhost:${AI_SERVICE_PORT:-8000}/health"
```

当 `embeddingModel` 为 `missing` 时，执行 `ollama pull nomic-embed-text` 后重启 `backend-ai`。如果更换嵌入模型导致向量维度不一致，应新建 Qdrant collection；不要直接让不同维度的模型共用原 collection。
