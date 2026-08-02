FROM ghcr.io/astral-sh/uv:0.12.1 AS uv

FROM python:3.12-slim

COPY --from=uv /uv /uvx /bin/

WORKDIR /app

ARG INSTALL_VISION=0

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml uv.lock ./
RUN if [ "$INSTALL_VISION" = "1" ]; then \
      uv sync --locked --group vision --no-dev --no-install-project; \
    else \
      uv sync --locked --no-dev --no-install-project; \
    fi

COPY app ./app
RUN if [ "$INSTALL_VISION" = "1" ]; then \
      uv sync --locked --group vision --no-dev; \
    else \
      uv sync --locked --no-dev; \
    fi

ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
