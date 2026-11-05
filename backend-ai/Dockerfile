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

# 1. 在基础镜像层，强制忽略并升级全局系统解释器的 setuptools
RUN pip install --no-cache-dir --break-system-packages --ignore-installed --upgrade "setuptools>=78.1.1"

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

# 💥 核心修复：1. 强行物理删除系统全局 Python 路径里 70.3.0 的残余目录
RUN rm -rf /usr/local/lib/python3.12/site-packages/setuptools-70.3.0* \
           /usr/local/lib/python3.12/site-packages/setuptools \
           /usr/lib/python3/dist-packages/setuptools*

# 💥 核心修复：2. 强制在全局环境与虚拟环境中同时覆盖安装最新安全版本
RUN /usr/local/bin/python -m pip install --no-cache-dir --upgrade "setuptools>=78.1.1" \
    && /app/.venv/bin/python -m pip install --no-cache-dir --upgrade "setuptools>=78.1.1"

ENV PATH="/app/.venv/bin:$PATH" \
    HOME=/tmp

RUN groupadd --gid 10001 skytrace \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin skytrace \
    && chown -R 10001:10001 /app
USER 10001:10001

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]