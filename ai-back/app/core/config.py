"""core.config · AI 模型配置加载（管理端动态配置 → Java runtime API 拉取）

数据源优先级：
1. Java 管理端 /api/ai/config/runtime（MySQL 存储，管理端动态维护）—— 内存缓存 TTL 30s
2. 本地 config/{providers,routing}.json —— Java 不可用时容灾兜底

字段命名：Java 返回 camelCase，本模块统一转为 snake_case 供 llm_router 消费。
"""
from __future__ import annotations

import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

CONFIG_DIR = Path(os.environ.get("AI_CONFIG_DIR", Path(__file__).resolve().parents[2] / "config"))

# 内网服务间 Token（05 章：仅接受内网调用）；为空表示开发模式不校验
INTERNAL_TOKEN = os.environ.get("AI_INTERNAL_TOKEN", "")


def validate_production_config():
    """生产环境配置 fail-closed 校验

    AI_ENV=prod 时 AI_INTERNAL_TOKEN 必须配置且长度 >= 32 字符，否则拒绝启动。
    """
    if os.environ.get("AI_ENV") == "prod":
        token = os.environ.get("AI_INTERNAL_TOKEN", "")
        if not token or len(token) < 32:
            print("[FATAL] 生产环境 AI_INTERNAL_TOKEN 必须配置且长度 >= 32 字符", file=sys.stderr)
            sys.exit(1)


# Java 管理端 runtime API（管理端动态配置下发入口）
RUNTIME_API_URL = os.environ.get(
    "AI_CONFIG_API_URL", "http://127.0.0.1:8080/api/ai/config/runtime")

# 内存缓存 TTL（秒）：管理端改库后最多 30s 生效
_CACHE_TTL = 30

# camelCase → snake_case 映射键（新增字段必须登记，否则运行时配置字段丢失）
_CAMEL_KEYS = {
    "baseUrl": "base_url", "requestPath": "request_path", "apiKey": "api_key",
    "apiKeyEnv": "api_key_env", "extraHeaders": "extra_headers",
    "timeoutSeconds": "timeout_seconds", "maxRetries": "max_retries",
    "retryTemperatureDecay": "retry_temperature_decay",
    "bodyTemplate": "body_template", "responseTextPath": "response_text_path",
    "maxTokens": "max_tokens",
    "webSearchProvider": "web_search_provider", "webSearchEnabled": "web_search_enabled",
}


def _to_snake(d: dict[str, Any]) -> dict[str, Any]:
    """camelCase → snake_case（顶层字段）"""
    out: dict[str, Any] = {}
    for k, v in d.items():
        out[_CAMEL_KEYS.get(k, k)] = v
    return out


class _LocalJsonFallback:
    """本地 JSON 文件容灾（Java 不可用时兜底，保留原 mtime 热更新）"""

    def __init__(self, path: Path):
        self._path = path
        self._lock = threading.Lock()
        self._mtime: float = -1.0
        self._data: dict[str, Any] = {}

    @property
    def data(self) -> dict[str, Any]:
        try:
            mtime = self._path.stat().st_mtime
        except OSError:
            return self._data
        if mtime != self._mtime:
            with self._lock:
                if mtime != self._mtime:
                    with open(self._path, encoding="utf-8") as f:
                        self._data = json.load(f)
                    self._mtime = mtime
        return self._data


_providers_json = _LocalJsonFallback(CONFIG_DIR / "providers.json")
_routing_json = _LocalJsonFallback(CONFIG_DIR / "routing.json")


class _RuntimeCache:
    """Java runtime API 拉取 + TTL 缓存 + 拉取失败回退本地 JSON"""

    def __init__(self):
        self._lock = threading.Lock()
        self._data: dict[str, Any] | None = None
        self._expire_at: float = 0.0

    def _fetch(self) -> dict[str, Any] | None:
        """从 Java /api/ai/config/runtime 拉取（X-Internal-Token 鉴权）"""
        try:
            # 内网 Token 为空（开发模式）时不带 X-Internal-Token 头；
            # Java 侧校验空串与缺失语义不同（缺失=null 才放行），带空串会误触发 401
            req = urllib.request.Request(
                RUNTIME_API_URL,
                headers={"X-Internal-Token": INTERNAL_TOKEN} if INTERNAL_TOKEN else {},
                method="GET",
            )
            with urllib.request.urlopen(req, timeout=3) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            if payload.get("code") != 200:
                return None
            data = payload.get("data") or {}
            return {
                "providers": [_to_snake(p) for p in data.get("providers", [])],
                "routes": [_to_snake(r) for r in data.get("routes", [])],
            }
        except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError):
            return None

    def get(self) -> dict[str, Any]:
        """返回最新配置：缓存有效则用缓存，否则拉取，拉取失败回退本地 JSON"""
        now = time.time()
        if self._data is not None and now < self._expire_at:
            return self._data
        with self._lock:
            if self._data is not None and now < self._expire_at:
                return self._data
            fresh = self._fetch()
            if fresh is not None:
                self._data = fresh
                self._expire_at = now + _CACHE_TTL
                return fresh
            # Java 不可用：回退本地 JSON（容灾），缓存 5s 避免高频重试
            from app.core.logging import get_logger
            get_logger("config").warning(
                f"Java runtime API 不可用，回退本地 JSON: {RUNTIME_API_URL}")
            self._data = self._fallback_local()
            self._expire_at = now + 5
            return self._data

    def _fallback_local(self) -> dict[str, Any]:
        """本地 JSON 兜底（字段结构与 runtime VO 对齐）"""
        providers = [
            {
                "name": p["name"], "vendor": p["vendor"],
                "protocol": _guess_protocol(p), "base_url": p["base_url"],
                "request_path": None, "api_key": os.environ.get(p.get("api_key_env") or "", ""),
                "api_key_env": p.get("api_key_env"), "models": p.get("models", []),
                "capabilities": p.get("capabilities", []), "extra_headers": None,
                "timeout_seconds": p.get("timeout_seconds", 8),
                "max_tokens": p.get("max_tokens"),
            }
            for p in _providers_json.data.get("providers", []) if p.get("enabled")
        ]
        return {"providers": providers, "routes": _routing_json.data.get("routes", {})}

    def invalidate(self):
        """手动失效缓存（测试或强制刷新用）"""
        with self._lock:
            self._data = None
            self._expire_at = 0.0


_runtime = _RuntimeCache()


def _guess_protocol(provider: dict[str, Any]) -> str:
    """本地 JSON 容灾的协议推导：显式 protocol 优先，否则按 vendor（OpenAI 兼容为主）"""
    if provider.get("protocol"):
        return provider["protocol"]
    if provider.get("vendor") == "internal":
        return "custom"
    return "openai"


def get_providers() -> list[dict[str, Any]]:
    return _runtime.get().get("providers", [])


def get_provider(name: str) -> dict[str, Any] | None:
    for p in get_providers():
        if p["name"] == name:
            return p
    return None


def get_route(capability: str) -> dict[str, Any]:
    routes = _runtime.get().get("routes", {})
    # routes 可能是 list（runtime API）或 dict（本地 JSON 的 routes 对象）
    if isinstance(routes, list):
        for r in routes:
            if r.get("capability") == capability:
                return r
        raise KeyError(f"未配置能力: {capability}")
    route = routes.get(capability)
    if route is None:
        raise KeyError(f"未配置能力: {capability}")
    return route
