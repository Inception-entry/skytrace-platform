FROM ghcr.io/astral-sh/uv:0.12.13 AS uv

FROM python:3.12-slim

COPY --from=uv /uv /uvx /bin/

WORKDIR /app

ARG INSTALL_VISION=0

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy

RUN set -eux; \
    apt_retry() { apt-get -o Acquire::Retries=5 -o Acquire::http::Timeout=30 "$@"; }; \
    i=0; \
    until apt_retry update; do \
      i=$((i + 1)); \
      [ "$i" -ge 5 ] && exit 1; \
      sleep $((i * 8)); \
    done; \
    apt_retry upgrade -y --no-install-recommends; \
    apt_retry install -y --no-install-recommends ffmpeg; \
    rm -rf /var/lib/apt/lists/*

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
