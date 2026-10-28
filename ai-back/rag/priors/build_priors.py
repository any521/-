"""rag.priors.build_priors · 3D 生成先验聚合（RAG-04 步骤 1）

输入：`data/*.json`（六类垃圾回收数据，18054 条；RAG-01 未交付时直接读取平铺）。
      **禁止 import RAG-01 的 indexing 模块**——本脚本自实现 load/clean，保持文件域隔离。
输出：`rag_data/priors_3d.json`（对齐 C2 的 CategoryPrior 契约，键 = sub_category 值）。

用法：`python -m rag.priors.build_priors`（在 ai-back 目录下执行）
"""
from __future__ import annotations

import json
import os
from collections import Counter, defaultdict
from datetime import datetime

from app.core.logging import get_logger

log = get_logger("rag.build_priors")

# ai-back 根目录（本文件位于 ai-back/rag/priors/ 下，向上三级）
_BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_DATA_DIR = os.path.join(_BASE_DIR, "data")
_OUT_DIR = os.path.join(_BASE_DIR, "rag_data")
_OUT_PATH = os.path.join(_OUT_DIR, "priors_3d.json")

# 六类数据文件（与 data/ 目录一致）
_DATA_FILES = ("recyclables", "hazardous", "kitchen", "other", "bulky", "e_waste")

# material → surface 映射表（决策完备，按表序首个命中为准；未匹配 → null）
# 注：木/竹 → "none"（木质感保持默认渲染），其他/未知 → null
_SURFACE_MAP = (
    (("纸", "纸板", "瓦楞", "纸箱", "书本", "报纸", "杂志"), "paper"),
    (("塑料", "PET", "HDPE", "PP", "LDPE", "PE", "膜"), "plastic"),
    (("铜", "铝", "铁", "钢", "金属", "合金", "不锈钢"), "metal"),
    (("玻璃", "石英", "瓶料"), "glass"),
    (("木", "竹"), "none"),
    (("其他", "未知"), None),
)

# 聚合阈值（决策完备）：top_names top 10 / forms top 5 / materials top 5
_TOP_NAMES = 10
_TOP_FORMS = 5
_TOP_MATERIALS = 5
# confidence 归一化：类别样本越多越可信，300 条封顶 1.0
_CONFIDENCE_CAP = 300


def _surface_of(material: str | None) -> str | None:
    """单个 material 值 → surface（含关键词即命中，映射表靠前者优先）；未匹配返回 None"""
    if not material:
        return None
    for keys, surface in _SURFACE_MAP:
        if any(k in material for k in keys):
            return surface
    return None


def _load_records() -> list[dict]:
    """自实现 load/clean（不依赖 RAG-01）：六个 data/*.json 平铺 records 并清洗。

    清洗规则：name 缺失/空白的记录丢弃（无法作为物品先验）；form/material 缺失用「未知」占位。
    """
    records: list[dict] = []
    for fname in _DATA_FILES:
        path = os.path.join(_DATA_DIR, f"{fname}.json")
        try:
            with open(path, encoding="utf-8") as fp:
                data = json.load(fp)
        except (OSError, ValueError) as e:
            log.warning("跳过数据文件 %s: %s", fname, e)
            continue
        for r in data.get("records") or []:
            name = (r.get("name") or "").strip()
            if not name:
                continue
            r = dict(r)
            r["name"] = name
            r["form"] = (r.get("form") or "").strip() or "未知"
            r["material"] = (r.get("material") or "").strip() or "未知"
            records.append(r)
    return records


def _aggregate(records: list[dict]) -> dict[str, dict]:
    """按 sub_category 聚合六类先验（键统一用 sub_category 值，与 C1 prices.json 的 by_category 键一致）"""
    by_cat: dict[str, list[dict]] = defaultdict(list)
    for r in records:
        cat = r.get("sub_category") or r.get("category")
        if cat:
            by_cat[cat].append(r)

    priors: dict[str, dict] = {}
    for cat, recs in by_cat.items():
        name_cnt = Counter(r["name"] for r in recs)
        form_cnt = Counter(r["form"] for r in recs)
        mat_cnt = Counter(r["material"] for r in recs)
        top1_mat = mat_cnt.most_common(1)[0][0] if mat_cnt else "未知"
        priors[cat] = {
            "category": cat,
            "count": len(recs),
            "top_names": [n for n, _ in name_cnt.most_common(_TOP_NAMES)],
            "forms": [f for f, _ in form_cnt.most_common(_TOP_FORMS)],
            "materials": [m for m, _ in mat_cnt.most_common(_TOP_MATERIALS)],
            "suggested_surface": _surface_of(top1_mat),
            "confidence": round(min(1.0, len(recs) / _CONFIDENCE_CAP), 4),
        }
    return priors


def build_priors() -> dict:
    """聚合主流程：load → clean → 按 sub_category 聚合 → 映射 surface → 组装产物"""
    records = _load_records()
    priors = _aggregate(records)
    return {
        "version": 1,
        "built_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "source_count": len(records),
        "priors": priors,
    }


def main() -> None:
    payload = build_priors()
    os.makedirs(_OUT_DIR, exist_ok=True)
    with open(_OUT_PATH, "w", encoding="utf-8") as fp:
        json.dump(payload, fp, ensure_ascii=False, indent=2)
    total = payload["source_count"]
    priors = payload["priors"]
    log.info("3D 先验聚合完成 source_count=%d categories=%d -> %s",
             total, len(priors), _OUT_PATH)
    for cat, p in priors.items():
        log.info("  %s: count=%d top_names=%d suggested_surface=%s confidence=%.2f",
                 cat, p["count"], len(p["top_names"]), p["suggested_surface"], p["confidence"])
    # 自检：top_names 非空、suggested_surface 合法性
    bad = [c for c, p in priors.items()
           if not p["top_names"] or p["suggested_surface"] not in (None, "paper", "plastic", "metal", "glass", "none")]
    if bad:
        log.error("先验自检失败（top_names 空或 surface 非法）: %s", bad)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
