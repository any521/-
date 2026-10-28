# 点废成金 v2.0 AI 旁路服务镜像（FastAPI，内网 8100，不对外暴露）
# 多阶段构建：builder 安装依赖，runtime 精简运行时
FROM python:3.11.9-slim-bookworm AS builder
WORKDIR /build
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
COPY requirements.txt .
RUN python -m venv /opt/venv \
    && /opt/venv/bin/pip install --upgrade pip \
    && /opt/venv/bin/pip install -r requirements.txt

# ---- V46 本地 ASR 模型（vosk 中文小模型，~42MB；hf-mirror 国内镜像，构建期下载解压）----
# 独立 RUN 层：requirements/代码不变时命中缓存，避免每次构建重复下载
RUN python -c "import urllib.request; urllib.request.urlretrieve('https://hf-mirror.com/alphacep/vosk-model-small-cn-0.22/resolve/main/vosk-model-small-cn-0.22.zip', '/build/vosk-model.zip')" \
    && python -c "import zipfile; zipfile.ZipFile('/build/vosk-model.zip').extractall('/build/models')" \
    && rm -f /build/vosk-model.zip \
    && ls /build/models

FROM python:3.11.9-slim-bookworm AS runtime
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai \
    PATH="/opt/venv/bin:$PATH" \
    AI_ENV=prod
# 复制虚拟环境
COPY --from=builder /opt/venv /opt/venv
# 复制 ASR 模型（vosk-model-small-cn-0.22 → /app/models）
COPY --from=builder /build/models /app/models
# 安装 Playwright Chromium 运行时依赖（不含浏览器本身，浏览器在 venv 内）
# 创建非 root 用户 app
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    libnss3 libnspr4 libdbus-1-3 libatk1.0-0 libatk-bridge2.0-0 \
    libcups2 libdrm2 libxkbcommon0 libxcomposite1 libxdamage1 \
    libxfixes3 libxrandr2 libgbm1 libasound2 libpango-1.0-0 \
    libcairo2 fonts-liberation \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app -d /app app \
    && mkdir -p /app/logs && chown -R app:app /app
COPY --chown=app:app . .
USER app
EXPOSE 8100
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8100/healthz > /dev/null || exit 1
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8100"]
