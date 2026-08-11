# SkyTrace AI Service

本地 AI 服务，通过 LangChain 调用 Ollama 中的无人机模型。

## 启动

```bash
cd backend-ai
uv sync
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000
```

如果本机 `8000` 已被其他进程占用，优先使用仓库根目录脚本，它会自动读取
`deploy/.env` 并复用当前本地依赖配置：

```bash
./scripts/start-local-ai.sh
```

## 验证

```bash
curl http://localhost:8000/

curl http://localhost:8000/health

curl -X POST http://localhost:8000/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"无人机失联后应该如何处理？"}'
```

如果通过 `./scripts/start-local-ai.sh` 启动，请把上面的 `8000` 替换成
`deploy/.env` 中的 `AI_SERVICE_PORT`。当前项目本地配置使用 `8001`。

## Docker 启动

在项目根目录执行：

```bash
./scripts/skytrace.sh rebuild backend-ai
./scripts/skytrace.sh logs backend-ai
```

## 可靠性配置

模型调用默认启用 120 秒单次超时和最多 2 次尝试。流式调用只会在
尚未产生任何模型输出时重试，避免把已经显示的 token 重复发送给前端。

```dotenv
AI_OLLAMA_TIMEOUT_SECONDS=120
AI_OLLAMA_MAX_ATTEMPTS=2
AI_OLLAMA_INITIAL_BACKOFF_SECONDS=0.5
AI_OLLAMA_MAX_BACKOFF_SECONDS=2
AI_DEPENDENCY_HEALTH_TIMEOUT_SECONDS=5
```

服务日志使用单行 JSON，并通过 `X-Request-Id` 串联 Java 与 AI 服务。
日志不会记录用户 Prompt、鉴权 Token 或完整模型答案。
