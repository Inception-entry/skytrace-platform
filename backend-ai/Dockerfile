FROM ghcr.io/astral-sh/uv:0.13.7 AS uv

FROM python:3.12-slim

COPY --from=uv /uv /uvx /bin/

WORKDIR /app

ARG INSTALL_VISION=0

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON=/usr/local/bin/python \
    UV_PYTHON_DOWNLOADS=never

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

# 👉 在这里升级系统的 pip 和 setuptools，修复 Trivy 扫出的高危漏洞
RUN pip install --no-cache-dir --upgrade "pip>=26.0" "setuptools>=78.1.1"

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
    fi \
    && uv cache clean

ENV PATH="/app/.venv/bin:$PATH" \
    HOME=/tmp

RUN groupadd --gid 10001 skytrace \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin skytrace \
    && chown -R 10001:10001 /app
USER 10001:10001

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]