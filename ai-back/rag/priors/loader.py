"""rag.priors.loader · 3D 先验降级加载（RAG-02 未交付时直接读 rag_data/priors_3d.json）

契约降级路径：C2 的 `category_prior(category)` 由 RAG-02 交付；未交付前 build3d_service
通过本模块读取先验文件 + 物品名匹配，等效拿到 CategoryPrior。任何失败返回 None/空结构，
调用方（build3d_service）走「无先验」原逻辑，绝不阻断。
"""
from __future__ import annotations

import json
import os
import threading

# ai-back 根目录（本文件位于 ai-back/rag/priors/ 下，向上三级）
_BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_PRIORS_PATH = os.path.join(_BASE_DIR, "rag_data", "priors_3d.json")

_lock = threading.Lock()
_cache: dict | None = None
_mtime: float | None = None

# 物品名常见前缀（回收语境）：匹配前剥离，避免「废塑料瓶」vs「塑料水瓶」表述差异漏匹配
_NAME_PREFIXES = ("废旧", "废弃", "污染", "破损", "损坏", "使用过的", "使用过", "废", "旧", "坏")


def _strip_prefix(name: str) -> str:
    """剥离物品名常见回收前缀，返回核心词（如 废塑料瓶 → 塑料瓶）"""
    for p in _NAME_PREFIXES:
        if name.startswith(p):
            return name[len(p):]
    return name


def load_priors() -> dict:
    """读取 priors_3d.json（进程内缓存 + mtime 感知热重载）；缺失/损坏返回空结构，不抛异常"""
    global _cache, _mtime
    try:
        m = os.path.getmtime(_PRIORS_PATH)
    except OSError:
        return _cache or {}
    with _lock:
        if _cache is None or _mtime != m:
            try:
                with open(_PRIORS_PATH, encoding="utf-8") as fp:
                    _cache = json.load(fp)
                _mtime = m
            except (OSError, ValueError):
                _cache = {}
                _mtime = m
        return _cache


def match_prior(item: str) -> dict | None:
    """按物品名匹配类别先验（降级路径，替代 keyword_search→category_prior 链路）。
    两级匹配：① item 与 top_names 去前缀后的核心词互相包含（双向子串）；
    ② item 含类别名核心词（如 玻璃杯→玻璃类、塑料水瓶→塑料类）。
    无匹配/异常返回 None（调用方走原逻辑）。"""
    try:
        if not item:
            return None
        item_core = _strip_prefix(item)
        data = load_priors()
        for cat, prior in (data.get("priors") or {}).items():
            for name in prior.get("top_names") or []:
                if not name:
                    continue
                core = _strip_prefix(name)
                if not core:
                    continue
                if item_core in core or core in item_core:
                    return prior
        # ② 类别名核心词（键去「类」字）：垃圾回收语境的日常物品名多直接含类别词
        for cat, prior in (data.get("priors") or {}).items():
            keyword = cat.replace("类", "").strip()
            if len(keyword) >= 2 and keyword in item:
                return prior
    except Exception:
        return None
    return None
