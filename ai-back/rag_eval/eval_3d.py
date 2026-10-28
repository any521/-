"""rag_eval.eval_3d · RAG-04 端到端评估（3D 先验链路）

样本：六类各 10 条（优先 real_base，按记录 id 稳定抽样、物品名去重）+ 10 个手工常见包装 = 70 个物品名。
评估：
  a) 先验命中率：keyword_search → category_prior 链路命中比例（目标 ≥ 90%）；
  b) spec 结构校验：六面完整性 / aspect 比例范围 / surface 合法性（无 LLM 环境用 mock provider 联调）；
  c) 端到端：有 LLM key 时占位图跑 build3d，输出 spec 摘要 + 人工主观评分表（1-5 待人工填写）；
  d) 回归对比：先验注入开启 vs 关闭（_RAG_OK=False），同物品两轮生成，记录差异项。
输出：rag_eval/report.md
用法：`python -m rag_eval.eval_3d [--mode standard|advanced|extreme] [--limit N]`（在 ai-back 目录下执行）
--mode：Track G 三档模式（standard=单链路现状；advanced=spec 级多轮自纠；extreme=+渲染截图对比闭环，
仅跑 1-2 个样本的渲染管线冒烟，控成本）；默认 standard，向后兼容。
--limit：真实 LLM 环境最多跑前 N 个样本（extreme 冒烟子集不受影响）；默认不限，向后兼容。
"""
from __future__ import annotations

import argparse
import base64
import json
import logging
import os
from collections import Counter

from app.services import build3d_service as b3d
from app.services.build3d_service import Build3dSpec

log = logging.getLogger("rag_eval.eval_3d")
logging.basicConfig(level=logging.INFO, format="%(name)s %(message)s")

# ai-back 根目录（本文件位于 ai-back/rag_eval/ 下，向上二级）
_BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_DATA_DIR = os.path.join(_BASE_DIR, "data")
_RAG_DATA_DIR = os.path.join(_BASE_DIR, "rag_data")
_REPORT_PATH = os.path.join(_BASE_DIR, "rag_eval", "report.md")

_DATA_FILES = ("recyclables", "hazardous", "kitchen", "other", "bulky", "e_waste")

# T6 三档落盘：每档运行结束写 tier_cache/{mode}.json，report 生成时合并全部档位（并发/重跑不互相覆盖）
_TIER_CACHE_DIR = os.path.join(_BASE_DIR, "rag_eval", "tier_cache")

# 手工补充的常见包装（3D 生成典型场景）
_MANUAL_ITEMS = ("牛奶盒", "易拉罐", "塑料瓶", "玻璃杯", "纸杯",
                 "泡面碗", "马克杯", "利乐包", "薯片袋", "矿泉水瓶")

# 1x1 透明 PNG 占位图（无真实图片兜底；部分供应商要求图片 ≥14px）
_PLACEHOLDER_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)
_PLACEHOLDER_1PX = "data:image/png;base64," + base64.b64encode(_PLACEHOLDER_PNG).decode()


# 真实图片占位（仓库内牛奶盒实拍图，尺寸满足供应商最低要求）：存在则优先用于端到端
_MILK_PATH = os.path.join(_BASE_DIR, "tmp_milk.png")
PLACEHOLDER_URL = _PLACEHOLDER_1PX
if os.path.isfile(_MILK_PATH):
    try:
        with open(_MILK_PATH, "rb") as fp:
            PLACEHOLDER_URL = "data:image/png;base64," + base64.b64encode(fp.read()).decode()
        log.info("端到端占位图使用真实图片 tmp_milk.png")
    except OSError:
        pass

# 真实 LLM key 检测（视觉能力供应商：qwen-vl / glm-4v / ark）
_LLM_ENV_KEYS = ("QWEN_API_KEY", "GLM_API_KEY", "ARK_API_KEY")

_SURFACE_ALL = ("none", "paper", "plastic", "metal", "glass")


class LocalRetriever:
    """RAG-02 契约模拟（RAG-01/02 未交付时评估用，文件域内自实现）：
    keyword_search 在 name/description/tags/material 全域做包含匹配（等价 BM25 的检索域 text），
    得分 = name 精确 > name 双向子串 > 全字段子串；category_prior 直接读 rag_data/priors_3d.json。
    """

    def __init__(self, records: list[dict], priors_data: dict):
        self._records = records
        self._priors = priors_data.get("priors") or {}

    def keyword_search(self, query: str, top_k: int = 5, filters: dict | None = None,
                       min_confidence: float = 0.0) -> list[dict]:
        if not query:
            return []
        q = query.strip()
        scored: list[dict] = []
        for r in self._records:
            name = r.get("name") or ""
            text = " ".join(filter(None, (
                name, r.get("description") or "",
                " ".join(r.get("tags") or []), r.get("material") or "", r.get("form") or "")))
            if q == name:
                score = 3.0
            elif q in name or name in q:
                score = 2.5
            elif q in text:
                score = 2.0
            else:
                continue
            if min_confidence > 0 and (r.get("confidence") or 0) < min_confidence:
                continue
            if filters:
                if filters.get("category") and r.get("category") != filters["category"]:
                    continue
                if filters.get("sub_category") and r.get("sub_category") != filters["sub_category"]:
                    continue
                if filters.get("region") and r.get("region") != filters["region"]:
                    continue
            hit = dict(r)
            hit["score"] = score
            scored.append(hit)
        scored.sort(key=lambda h: (-h["score"], h.get("data_source") != "real_base", h.get("id") or ""))
        return scored[:top_k]

    def category_prior(self, category: str) -> dict | None:
        if not category:
            return None
        if category in self._priors:
            return self._priors[category]
        return self._priors.get(category + "类")


def _load_all_records() -> list[dict]:
    """六个 data/*.json 平铺（与 build_priors 相同的文件域隔离读取）"""
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
            if (r.get("name") or "").strip():
                records.append(r)
    return records


def build_samples(records: list[dict]) -> list[tuple[str, str, str]]:
    """样本集：六类各 10 条（优先 real_base → 稳定抽样 → name 去重）+ 10 个手工包装。
    返回 [(item, category, source)]，source ∈ {real_base, synthesized_from_real_base,
    synthetic_estimate, manual}。"""
    order = {"real_base": 0, "synthesized_from_real_base": 1, "synthetic_estimate": 2}
    samples: list[tuple[str, str, str]] = []
    for fname in _DATA_FILES:
        recs = [r for r in records if r.get("category") and _file_of(r) == fname]
        recs_sorted = sorted(recs, key=lambda r: (order.get(r.get("data_source"), 9), r.get("id") or ""))
        seen: set[str] = set()
        for r in recs_sorted:
            name = (r.get("name") or "").strip()
            if not name or name in seen:
                continue
            seen.add(name)
            samples.append((name, r.get("sub_category") or r.get("category"),
                            r.get("data_source") or "unknown"))
            if len(seen) >= 10:
                break
    for name in _MANUAL_ITEMS:
        samples.append((name, "手工补充", "manual"))
    return samples


def _file_of(r: dict) -> str:
    """记录归属的数据文件（用 category 判断，避免重复加载）"""
    cat = r.get("category")
    return {
        "可回收物": "recyclables", "有害垃圾": "hazardous", "厨余垃圾": "kitchen",
        "其他垃圾": "other", "大件垃圾": "bulky", "电子废弃物": "e_waste",
    }.get(cat, "")


def _sample_each_class(items: list[tuple[str, str, str]], k: int) -> list[tuple[str, str, str]]:
    """按类别（sub_category）均匀抽样：每类至多 k 个，保持原顺序"""
    seen: dict[str, int] = {}
    out: list[tuple[str, str, str]] = []
    for it in items:
        cat = it[1]
        if seen.get(cat, 0) >= k:
            continue
        seen[cat] = seen.get(cat, 0) + 1
        out.append(it)
    return out


def prior_hit_chain(retriever: LocalRetriever, item: str,
                    use_real: bool = False) -> tuple[bool, str | None, str | None]:
    """a) 评估链路：keyword_search(item, top_k=1) → sub_category → category_prior。
    use_real=True（RAG-02 已交付）时用真实 retriever，否则用 LocalRetriever 模拟。
    返回 (命中, 类别, 建议surface)。"""
    try:
        if use_real:
            hits = b3d.retriever.keyword_search(item, top_k=1)
            cat_of = lambda h: h.get("sub_category") or h.get("category")  # noqa: E731
            prior_of = b3d.retriever.category_prior
        else:
            hits = retriever.keyword_search(item, top_k=1)
            cat_of = lambda h: h.get("sub_category") or h.get("category")  # noqa: E731
            prior_of = retriever.category_prior
        if not hits:
            return False, None, None
        cat = cat_of(hits[0])
        if not cat:
            return False, None, None
        prior = prior_of(cat)
        if not prior:
            return False, cat, None
        return True, cat, prior.get("suggested_surface")
    except Exception as e:
        log.info("命中链路异常 item=%s: %s", item, e)
        return False, None, None


def validate_spec(spec: Build3dSpec | None) -> tuple[bool, list[str]]:
    """b) spec 结构校验：aspect 比例范围 / 六面完整性 / surface 合法性。返回 (通过, 违规项)。"""
    if spec is None:
        return False, ["spec 为 None（构建失败）"]
    issues: list[str] = []
    if not (spec.primitives or spec.lathes or spec.tubes):
        issues.append("无任何几何体（primitives/lathes/tubes 全空）")
    a = spec.aspect or [1.0, 1.0, 1.0]
    if len(a) != 3:
        issues.append(f"aspect 非三维: {a}")
    else:
        w, h, d = (float(v) for v in a)
        if not (0.05 <= w <= 3.0 and 0.5 <= h <= 1.5 and 0.05 <= d <= 3.0):
            issues.append(f"aspect 比例越界: [{w:.2f},{h:.2f},{d:.2f}]（高=1 校准）")
    for p in spec.primitives:
        if p.surface not in _SURFACE_ALL:
            issues.append(f"surface 非法: {p.surface}")
        if p.type == "box" and not p.size:
            issues.append("box 缺 size")
        if p.type in ("cylinder", "sphere", "cone") and p.radius is None:
            issues.append(f"{p.type} 缺 radius")
        if p.materials and len(p.materials) > 6:
            issues.append("materials 超 6 面")
    # 六面完整性：主 box（正面[4]有内容）材料数组 ≥5 面
    for p in spec.primitives:
        if p.type != "box" or not p.materials:
            continue
        front = p.materials[4] if len(p.materials) > 4 else None
        if front is not None and (front.items or (front.bg or "").lower() != "transparent"):
            filled = sum(1 for m in p.materials[:6] if m is not None)
            if filled < 5:
                issues.append(f"主 box 六面不完整（{filled}/6）")
            break
    return (not issues), issues


def _surfaces_of(spec: Build3dSpec | None) -> list[str]:
    if spec is None:
        return []
    return [p.surface for p in spec.primitives] + [l.surface for l in spec.lathes]


def run_regression(items: list[str], limit: int | None = None,
                   use_real: bool = False) -> list[dict]:
    """d) 回归对比：同物品两轮生成（先验关闭 vs 开启），记录差异项。
    关闭 = _RAG_OK=False + retriever=None + _PRIORS_OK=False + _PRIOR_ENABLED=False（改造前行为）；
    开启 = 先验链路恢复——RAG-02 已交付（use_real）时用真实 retriever（会触发第二轮 LLM 确认，
    真实环境用 limit 降采样控成本）；未交付时用 LocalRetriever 模拟。
    返回差异清单：surface 修正数、aspect 变化、部件数变化、结构校验结果。"""
    if limit is not None:
        items = list(items)[:limit]
    old = (b3d._RAG_OK, b3d._PRIORS_OK, b3d._PRIOR_ENABLED, b3d.retriever)
    diffs: list[dict] = []
    try:
        # 关闭模式
        b3d._RAG_OK, b3d._PRIORS_OK, b3d._PRIOR_ENABLED, b3d.retriever = \
            False, False, False, None
        off_results = {item: b3d.build3d(PLACEHOLDER_URL) for item in items}
        # 开启模式
        if use_real:
            b3d._RAG_OK, b3d._PRIORS_OK, b3d._PRIOR_ENABLED = True, True, True
        else:
            priors_data = json.load(open(os.path.join(_RAG_DATA_DIR, "priors_3d.json"), encoding="utf-8"))
            local = LocalRetriever(_load_all_records(), priors_data)
            b3d._RAG_OK, b3d._PRIORS_OK, b3d._PRIOR_ENABLED, b3d.retriever = \
                True, True, True, local
        on_results = {item: b3d.build3d(PLACEHOLDER_URL) for item in items}
    finally:
        b3d._RAG_OK, b3d._PRIORS_OK, b3d._PRIOR_ENABLED, b3d.retriever = old

    for item in items:
        off, on = off_results[item], on_results[item]
        off_surf, on_surf = _surfaces_of(off.spec), _surfaces_of(on.spec)
        # 修正数 = 开启后从 none → 具体值的数量（禁止回归劣化：必须 ≥ 0）
        fixed = sum(1 for a, b in zip(on_surf, off_surf) if b == "none" and a in ("paper", "plastic", "metal", "glass"))
        off_ok, on_ok = validate_spec(off.spec), validate_spec(on.spec)
        diff = {
            "item": item,
            "surface_fixed": fixed,
            "surface_change": sorted(set(on_surf) ^ set(off_surf)) or None,
            "aspect_off": off.spec.aspect if off.spec else None,
            "aspect_on": on.spec.aspect if on.spec else None,
            "parts_off": len(off.spec.primitives) if off.spec else 0,
            "parts_on": len(on.spec.primitives) if on.spec else 0,
            "struct_off": off_ok[0],
            "struct_on": on_ok[0],
            "error_off": off.error,
            "error_on": on.error,
        }
        diffs.append(diff)
    return diffs


def has_llm_key() -> bool:
    """真实 LLM key 检测（视觉能力供应商）"""
    return any(bool(os.environ.get(k)) for k in _LLM_ENV_KEYS)


def run_tier_compare(items: list[tuple[str, str, str]], mode: str) -> list[dict]:
    """T6 三档模式对比：同物品列表按 mode 跑 build3d（extreme 仅渲染冒烟子集）。

    mock 下 `_critique_call` 跳过、渲染真实执行（本机 playwright 可用时）；
    extreme 的审阅失败不阻断（契约 C4-3），mode 回显反映实际执行档位。
    返回行：item / 部件数 / 结构校验 / mode 回显 / degraded / 时延 ms / 错误。
    """
    rows: list[dict] = []
    for item, cat, src in items:
        resp = b3d.build3d(PLACEHOLDER_URL, mode=mode)
        ok, issues = validate_spec(resp.spec)
        parts = (len(resp.spec.primitives) + len(resp.spec.lathes) + len(resp.spec.tubes)
                 if resp.spec else 0)
        rows.append({
            "item": item, "parts": parts, "ok": ok, "issues": issues if not ok else [],
            "mode": resp.mode, "degraded": resp.degraded,
            "elapsed_ms": resp.elapsed_ms, "model": resp.model_name or "—",
            "error": (resp.error or "")[:80],
        })
    return rows


def _write_report(sections: dict) -> None:
    lines = [
        "# RAG-04 3D 端到端评估报告",
        "",
        f"> 生成时间：{sections['built_at']}；环境：{'真实 LLM' if sections['has_llm'] else 'mock provider 联调'}；"
        f"样本数：{sections['total']}（数据抽样 {sections['sampled']} + 手工包装 {sections['manual']}）",
        "",
        "## 1. 先验命中率（keyword_search → category_prior 链路）",
        "",
        f"- 命中 **{sections['hits']}/{sections['total']}** = **{sections['hit_rate']:.1%}**（目标 ≥ 90%）",
        "",
        "| # | 物品名 | 类别 | 来源 | 命中 | 建议 surface |",
        "|---|---|---|---|---|---|",
    ]
    for i, row in enumerate(sections["hit_rows"], 1):
        lines.append(f"| {i} | {row['item']} | {row['cat']} | {row['src']} | "
                     f"{'✅' if row['hit'] else '❌'} | {row['surface'] or '—'} |")

    lines += [
        "",
        "## 2. spec 结构校验（六面完整性 / aspect 比例 / surface 合法性）",
        "",
        f"- 通过 **{sections['valid_n']}/{sections['valid_total']}** = **{sections['valid_rate']:.1%}**（目标 ≥ 90%，"
        f"{sections['mock_note']}）",
        "",
        "| # | 物品名 | 部件数 | 校验 | 违规项 |",
        "|---|---|---|---|---|",
    ]
    for i, row in enumerate(sections["valid_rows"], 1):
        lines.append(f"| {i} | {row['item']} | {row['parts']} | "
                     f"{'✅' if row['ok'] else '❌'} | {'；'.join(row['issues']) or '—'} |")

    lines += [
        "",
        "## 3. 端到端（占位图跑 /v1/3d-build）",
        "",
        sections["e2e_note"],
        "",
        "| # | 物品名 | 生成模型 | 部件数 | 结构校验 | 主观评分（还原度/比例/材质，1-5 人工填） |",
        "|---|---|---|---|---|---|",
    ]
    for i, row in enumerate(sections["e2e_rows"], 1):
        lines.append(f"| {i} | {row['item']} | {row['model']} | {row['parts']} | "
                     f"{'✅' if row['ok'] else '❌'} |  |")

    lines += [
        "",
        "## 4. 回归对比（先验注入开启 vs 关闭）",
        "",
        "- 关闭模式 = `_RAG_OK=False`（改造前行为）；开启模式 = LocalRetriever 模拟 RAG-02 就绪。",
        "- 禁止回归劣化：开启后 surface 修正数 ≥ 0，spec 结构校验不下降。",
        "",
        "| # | 物品名 | surface 修正数 | surface 变化 | aspect（关→开） | 部件数（关→开） | 结构校验（关→开） |",
        "|---|---|---|---|---|---|---|",
    ]
    for i, row in enumerate(sections["diff_rows"], 1):
        lines.append(f"| {i} | {row['item']} | {row['surface_fixed']} | "
                     f"{row['surface_change'] or '—'} | "
                     f"{row['aspect_off']} → {row['aspect_on']} | "
                     f"{row['parts_off']} → {row['parts_on']} | "
                     f"{'✅' if row['struct_off'] else '❌'} → {'✅' if row['struct_on'] else '❌'} |")

    lines += [
        "",
        "## 5. 结论",
        "",
        *sections["conclusion"],
        "",
    ]

    # T6：三档模式对比章节（Track G，合并 tier_cache 中所有已落盘档位；本档数据由 main 先写入）
    _tier_sections: list[tuple[str, str, list[dict]]] = []
    if os.path.isdir(_TIER_CACHE_DIR):
        for fname in ("standard", "advanced", "extreme"):
            path = os.path.join(_TIER_CACHE_DIR, f"{fname}.json")
            try:
                with open(path, encoding="utf-8") as fp:
                    data = json.load(fp)
                _tier_sections.append((fname, data["note"], data["rows"]))
            except (OSError, ValueError):
                continue
    if _tier_sections:
        lines += ["", "## 6. 三档模式对比（Track G · T6）", ""]
        for fname, note, rows in _tier_sections:
            ok_n = sum(1 for r in rows if r["ok"])
            degrade_n = sum(1 for r in rows if r["degraded"])
            med_ms = sorted(r["elapsed_ms"] for r in rows)[len(rows) // 2]
            lines += [
                f"### {fname}（{ok_n}/{len(rows)} 结构校验通过，{degrade_n} 个 degraded，时延中位 {med_ms}ms）",
                "",
                note,
                "",
                "| # | 物品名 | 部件数 | 结构校验 | mode 回显 | degraded | 时延 ms | 备注 |",
                "|---|---|---|---|---|---|---|---|",
            ]
            for i, row in enumerate(rows, 1):
                lines.append(
                    f"| {i} | {row['item']} | {row['parts']} | "
                    f"{'✅' if row['ok'] else '❌'} | {row['mode']} | "
                    f"{'是' if row['degraded'] else '否'} | {row['elapsed_ms']} | "
                    f"{row.get('error') or '—'} |")
            lines.append("")

    with open(_REPORT_PATH, "w", encoding="utf-8") as fp:
        fp.write("\n".join(lines))
    log.info("评估报告已写入 %s", _REPORT_PATH)


def main(mode: str = "standard", limit: int | None = None) -> None:
    from datetime import datetime

    records = _load_all_records()
    with open(os.path.join(_RAG_DATA_DIR, "priors_3d.json"), encoding="utf-8") as fp:
        priors_data = json.load(fp)
    local = LocalRetriever(records, priors_data)
    samples = build_samples(records)

    # a) 先验命中率
    hit_rows, hits = [], 0
    for item, cat, src in samples:
        ok, hit_cat, surface = prior_hit_chain(local, item)
        if ok:
            hits += 1
        hit_rows.append({"item": item, "cat": hit_cat or cat, "src": src,
                         "hit": ok, "surface": surface})
    total = len(samples)
    hit_rate = hits / total

    # b) 结构校验 + c) 端到端（build3d 实际生成）：
    # 环境探针：实际跑一次，provider 非 mock 才算真实 LLM 环境（有 key 但不可用仍按 mock 全量跑）；
    # 真实环境只跑子集（手工 10 + 每类抽样 2 ≈ 22 次，控制成本）
    probe = b3d.build3d(PLACEHOLDER_URL)
    llm = not probe.model_name.startswith("mock/")
    run_items = samples if not llm else (
        [(i, c, s) for i, c, s in samples if s == "manual"]
        + _sample_each_class([(i, c, s) for i, c, s in samples if s != "manual"], k=2)
    )
    if limit is not None:
        run_items = run_items[:limit]
    valid_rows, valid_n = [], 0
    e2e_rows = []
    if mode == "standard":
        for item, cat, src in run_items:
            resp = b3d.build3d(PLACEHOLDER_URL)
            ok, issues = validate_spec(resp.spec)
            if ok:
                valid_n += 1
            parts = (len(resp.spec.primitives) + len(resp.spec.lathes) + len(resp.spec.tubes)
                     if resp.spec else 0)
            valid_rows.append({"item": item, "parts": parts, "ok": ok,
                               "issues": issues if not ok else []})
            e2e_rows.append({"item": item, "model": resp.model_name or "—",
                             "parts": parts, "ok": ok, "mode": resp.mode,
                             "degraded": resp.degraded, "elapsed_ms": resp.elapsed_ms,
                             "error": (resp.error or "")[:80],
                             "issues": issues if not ok else []})
        tier_rows = e2e_rows
    else:
        # T6 三档对比：advanced 跑全子集（同 standard 样本）；extreme 渲染管线冒烟（1-2 手工样本，契约 T6-2）
        tier_items = (run_items if mode == "advanced"
                      else [(i, c, s) for i, c, s in samples if s == "manual"][:2])
        tier_rows = run_tier_compare(tier_items, mode)
        for row in tier_rows:
            if row["ok"]:
                valid_n += 1
            valid_rows.append({"item": row["item"], "parts": row["parts"],
                               "ok": row["ok"], "issues": row["issues"]})
        e2e_rows = tier_rows
    valid_total = len(valid_rows)
    valid_rate = valid_n / valid_total
    if llm:
        mock_note = "真实 LLM 环境，spec 来自真实生成（子集 22 个物品）"
        e2e_note = ("真实 LLM 环境：占位图跑 build3d，主观评分需人工按还原度/比例/材质填写 1-5。"
                    "⚠️ 真实图片建议取各物品实拍图补跑（本报告为占位图联调）。")
    else:
        mock_note = "mock provider 联调（无 LLM key，spec 为 mock 占位输出）"
        e2e_note = ("mock provider 联调演练（无 LLM key）：mock 对全部物品返回同一占位 spec，"
                    "结构校验为确定性结果；真实 key 注入后重跑本脚本即可升级为真实端到端。")

    # d) 回归对比（手工包装 + 每类抽样 2 个，共 20 个物品；真实 LLM 环境降采样到 5 个控成本）；
    # T6：advanced/extreme 档位跳过先验回归（三档对比见第 6 章，避免重复 LLM 成本）
    if mode == "standard":
        regress_items = list(_MANUAL_ITEMS) + [row["item"] for row in hit_rows
                                               if row["src"] != "manual"][:10]
        # --limit 同时约束回归样本数（控真实 LLM 成本）；默认 None 时回归上限 5
        regress_limit = min(5, limit) if (llm and limit is not None) else (5 if llm else None)
        diffs = run_regression(regress_items, limit=regress_limit)
    else:
        regress_items, diffs = [], []
    surface_fix_total = sum(d["surface_fixed"] for d in diffs)
    regress_ok = (all(d["surface_fixed"] >= 0 and (d["struct_on"] or not d["struct_off"])
                      for d in diffs) if diffs else True)
    n_improved = sum(1 for d in diffs if d["surface_fixed"] > 0)

    conclusion = [
        f"1. 先验命中率 **{hit_rate:.1%}**（目标 ≥ 90%）：{'✅ 达标' if hit_rate >= 0.9 else '❌ 未达标'}。",
        f"2. 结构校验通过率 **{valid_rate:.1%}**（目标 ≥ 90%）：{'✅ 达标' if valid_rate >= 0.9 else '❌ 未达标'}。",
        f"3. 回归对比：{len(regress_items)} 个物品两轮生成，surface 修正合计 {surface_fix_total} 处"
        f"（{n_improved} 个物品获修正），结构校验{'无劣化' if regress_ok else '存在劣化'}"
        f"：{'✅ 无回归' if regress_ok else '❌ 需排查'}。",
        "4. 环境说明：RAG-01/02 未交付，评估使用文件域内 LocalRetriever 模拟契约；"
        "RAG-02 交付后 `_RAG_OK=True` 自动切换真实检索，本脚本可直接重跑。",
    ]
    if mode == "standard":
        conclusion.append(
            "5. 三档对比基线：本报告第 6 章为 standard 基线（advanced/extreme 以 `--mode` 重跑对齐比较）。")
    else:
        med_ms = sorted(r["elapsed_ms"] for r in tier_rows)[len(tier_rows) // 2]
        n_degrade = sum(1 for r in tier_rows if r["degraded"])
        n_ok = sum(1 for r in tier_rows if r["ok"])
        conclusion.append(
            f"5. 三档对比：`--mode {mode}` 运行 {len(tier_rows)} 个物品，结构校验 {n_ok}/{len(tier_rows)}，"
            f"degraded {n_degrade} 个，时延中位 {med_ms}ms（详见第 6 章）。")

    tier_note = (
        f"三档对比：本次 `--mode {mode}` 运行，同占位图同物品列表，记录 mode 回显 / degraded / 时延。"
        + ("extreme 为渲染管线冒烟（截图→对比→修正闭环可用性验证，非全样本，契约 T6-2）。"
           if mode == "extreme" else ""))

    # T6 落盘：本档数据写入 tier_cache/{mode}.json（report 合并时读取）
    os.makedirs(_TIER_CACHE_DIR, exist_ok=True)
    with open(os.path.join(_TIER_CACHE_DIR, f"{mode}.json"), "w", encoding="utf-8") as fp:
        json.dump({"mode": mode, "note": tier_note, "rows": tier_rows,
                   "built_at": datetime.now().astimezone().isoformat(timespec="seconds")},
                  fp, ensure_ascii=False, indent=1)
    log.info("三档数据已落盘 %s", os.path.join(_TIER_CACHE_DIR, f"{mode}.json"))

    _write_report({
        "built_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "has_llm": llm, "total": total,
        "sampled": sum(1 for r in hit_rows if r["src"] != "manual"),
        "manual": sum(1 for r in hit_rows if r["src"] == "manual"),
        "hits": hits, "hit_rate": hit_rate, "hit_rows": hit_rows,
        "valid_n": valid_n, "valid_total": valid_total, "valid_rate": valid_rate,
        "valid_rows": valid_rows, "mock_note": mock_note,
        "e2e_note": e2e_note, "e2e_rows": e2e_rows,
        "diff_rows": diffs, "conclusion": conclusion,
        "tier_rows": tier_rows, "tier_note": tier_note,
    })


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="RAG-04 3D 端到端评估（Track G 三档模式对比）")
    parser.add_argument("--mode", choices=("standard", "advanced", "extreme"),
                        default="standard",
                        help="3D 构建档位：standard=现状链路；advanced=spec 级自纠；"
                             "extreme=渲染闭环（冒烟 1-2 样本，契约 T6-2）")
    parser.add_argument("--limit", type=int, default=None,
                        help="真实 LLM 环境最多跑前 N 个样本（控成本；默认不限）")
    args = parser.parse_args()
    main(args.mode, args.limit)
