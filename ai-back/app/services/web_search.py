"""services.web_search · 客服/语音助手的联网搜索增强（可选能力，静默降级）

与 RAG 并列的第二个知识源：prompt 预检索注入，非 function calling。
provider 可插拔（bocha / tavily），配置优先级：

1. 管理端「AI 供应商」页承载行（ai_provider 表）：行名 name='web-search'
   或能力标签含 search 的行（如「博查」+ 能力标签 search）：
   web_search_enabled=1 启用；web_search_provider 选 bocha/tavily/空=auto；
   密钥经 api_key 列（AES 加密入库）或 api_key_env（环境变量）。经
   /api/ai/config/runtime 拉取，管理端改配置 30s 内热更新，无需重启。
   接口地址优先用行内 base_url + request_path（如 https://api.bocha.cn/v1/web-search），
   未填则用默认 https://api.bochaai.com/v1/web-search。
2. 环境变量兜底（管理端未配/不可用时）：BOCHA_API_KEY / TAVILY_API_KEY /
   AI_WEBSEARCH_PROVIDER（auto/bocha/tavily/off）。环境变量每次调用即时读取，
   进程内改环境后无需重启。

全部未配置或调用失败：search() 返回空列表，调用方自然降级（不阻断主流程）。
"""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from typing import Any

from app.core.logging import get_logger

log = get_logger("web_search")

# 管理端承载行：name 恰为 web-search，或能力标签含 search
_ADMIN_ROW_NAME = "web-search"
_ADMIN_CAPABILITY = "search"

_TIMEOUT_SECONDS = 8
_MAX_QUERY_CHARS = 100
_MAX_RESULTS = 5

# 博查默认接口（管理端行内 base_url + request_path 优先，未填才用默认）
_BOCHA_URL = "https://api.bochaai.com/v1/web-search"
_TAVILY_URL = "https://api.tavily.com/search"


def _admin_row() -> dict[str, Any] | None:
    """读取管理端 web-search 承载行（name='web-search' 或能力标签含 search）；
    runtime 拉取（30s TTL 热更新）；失败/不存在返回 None"""
    try:
        from app.core import config as ai_config
        for p in ai_config.get_providers():
            name = (p.get("name") or "").strip().lower()
            caps = p.get("capabilities") or []
            if name == _ADMIN_ROW_NAME or any(
                    isinstance(c, str) and c.strip().lower() == _ADMIN_CAPABILITY for c in caps):
                return p
        return None
    except Exception as e:
        log.warning(f"WebSearch 管理端配置读取降级: {e}")
        return None


def _bocha_url(row: dict[str, Any] | None) -> str:
    """博查接口地址：行内 base_url + request_path 优先，否则默认地址"""
    base = ((row or {}).get("base_url") or "").strip().rstrip("/")
    path = ((row or {}).get("request_path") or "").strip()
    if base and path:
        return base + (path if path.startswith("/") else "/" + path)
    if base:
        return base + "/v1/web-search"
    return _BOCHA_URL


def _resolve() -> tuple[str | None, str, dict[str, Any] | None]:
    """解析 (provider, api_key, row)；provider=None 表示整体停用（降级）。
    环境变量每次调用即时读取，进程内热更新无需重启。"""
    row = _admin_row()
    admin_enabled = bool(row and (row.get("web_search_enabled") or 0) == 1)
    admin_provider = (row or {}).get("web_search_provider") or ""
    admin_key = (row or {}).get("api_key") or ""
    admin_key_env = (row or {}).get("api_key_env") or ""

    env_provider = os.environ.get("AI_WEBSEARCH_PROVIDER", "")
    env_bocha = os.environ.get("BOCHA_API_KEY", "")
    env_tavily = os.environ.get("TAVILY_API_KEY", "")

    provider = (env_provider or admin_provider or "auto").lower()
    if provider == "off":
        return None, "", row
    if provider not in ("bocha", "tavily", "auto"):
        provider = "auto"

    key = admin_key if admin_enabled else ""
    if not key:
        key = env_bocha if provider in ("auto", "bocha") else env_tavily
    if not key and provider == "auto":
        key = env_tavily or env_bocha
    if not key and admin_key_env:
        key = os.environ.get(admin_key_env, "")
    if not key:
        return None, "", row
    if provider == "auto":
        provider = "bocha" if (env_bocha or admin_key) else "tavily"
    return provider, key, row


def _post_json(url: str, body: dict[str, Any], headers: dict[str, str]) -> dict[str, Any]:
    """标准库 POST JSON（项目惯例：不引 SDK，超时/网络错误上抛由调用方降级）"""
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", **headers},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=_TIMEOUT_SECONDS) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _search_bocha(query: str, count: int, api_key: str, url: str) -> list[dict[str, str]]:
    """博查 Web Search API（返回 name/url/snippet + 可选总结）"""
    payload = _post_json(
        url,
        {"query": query, "count": count, "freshness": "noLimit", "summary": True},
        {"Authorization": f"Bearer {api_key}"},
    )
    web_pages = ((payload.get("data") or {}).get("webPages") or {}).get("value") or []
    results = [
        {"title": p.get("name", ""), "url": p.get("url", ""), "snippet": p.get("snippet", "")}
        for p in web_pages[:count]
    ]
    summary = (payload.get("data") or {}).get("summary") or ""
    if summary and results:
        results.insert(0, {"title": "搜索总结", "url": "", "snippet": summary})
    return results


def _search_tavily(query: str, count: int, api_key: str) -> list[dict[str, str]]:
    """Tavily Search API（basic 深度 1 credit/次）"""
    payload = _post_json(
        _TAVILY_URL,
        {"api_key": api_key, "query": query, "max_results": count,
         "search_depth": "basic", "include_answer": True},
        {},
    )
    answer = payload.get("answer") or ""
    results = [
        {"title": r.get("title", ""), "url": r.get("url", ""), "snippet": r.get("content", "")}
        for r in payload.get("results", [])[:count]
    ]
    if answer and results:
        results.insert(0, {"title": "搜索总结", "url": "", "snippet": answer})
    return results


def search(query: str, count: int = _MAX_RESULTS) -> list[dict[str, str]]:
    """联网搜索：失败/未配置一律返回空列表（与 RAG 检索同级降级，不阻断主流程）"""
    provider, api_key, row = _resolve()
    if provider is None:
        return []
    q = (query or "").strip()[:_MAX_QUERY_CHARS]
    if not q:
        return []
    try:
        if provider == "bocha":
            hits = _search_bocha(q, count, api_key, _bocha_url(row))
        else:
            hits = _search_tavily(q, count, api_key)
        if hits:
            log.info(f"WebSearch 命中 provider={provider} query={q[:40]} hits={len(hits)}")
        return hits
    except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as e:
        log.warning(f"WebSearch 降级 provider={provider}: {e}")
        return []


def to_prompt_snippet(hits: list[dict[str, str]], limit: int = _MAX_RESULTS) -> str:
    """检索结果 → prompt 注入片段；空结果返回空串"""
    if not hits:
        return ""
    lines = []
    for h in hits[:limit]:
        title = h.get("title") or ""
        url = h.get("url") or ""
        snippet = h.get("snippet") or ""
        lines.append(f"- {title}" + (f"（{url}）" if url else "") + (f"：{snippet}" if snippet else ""))
    return "\n【联网搜索结果（仅供参考，可能过时，回复需自行判断）】\n" + "\n".join(lines)


if __name__ == "__main__":  # 冒烟：python -m app.services.web_search "易拉罐回收价格"
    import sys

    query = sys.argv[1] if len(sys.argv) > 1 else "易拉罐回收价格"
    provider, _, row = _resolve()
    url = _bocha_url(row) if provider == "bocha" else "（tavily）"
    print(f"provider 生效: {provider or '(未配置任何 key)'}")
    print(f"接口地址: {url}")
    hits = search(query)
    print(to_prompt_snippet(hits) if hits else "（无结果：未配置 key 或调用失败）")
