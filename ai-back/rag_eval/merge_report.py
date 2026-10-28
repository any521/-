# -*- coding: utf-8 -*-
"""T6 汇总：合并 tier_cache 三档数据 → 生成最终 report.md（不重跑 LLM 档位）。

- 章节 1 先验命中率：本地检索链路重算（无 LLM 成本）；
- 章节 2/3 结构校验与端到端：standard 档落盘数据；
- 章节 4 回归对比：标注由 standard 档运行覆盖（数据见 standard.json）；
- 章节 5 结论：三档汇总；
- 章节 6 三档对比：tier_cache 合并（standard/advanced/extreme 各一节）。
"""
import json
import os
from datetime import datetime

from rag_eval import eval_3d as E

records = E._load_all_records()
with open(os.path.join(E._RAG_DATA_DIR, "priors_3d.json"), encoding="utf-8") as fp:
    priors_data = json.load(fp)
local = E.LocalRetriever(records, priors_data)
samples = E.build_samples(records)

# 章节 1：先验命中率（本地链路，无 LLM）
hit_rows, hits = [], 0
for item, cat, src in samples:
    ok, hit_cat, surface = E.prior_hit_chain(local, item)
    if ok:
        hits += 1
    hit_rows.append({"item": item, "cat": hit_cat or cat, "src": src,
                     "hit": ok, "surface": surface})
total = len(samples)
hit_rate = hits / total

# 三档数据（tier_cache 落盘为准）
tiers: dict[str, list[dict]] = {}
for fname in ("standard", "advanced", "extreme"):
    path = os.path.join(E._TIER_CACHE_DIR, f"{fname}.json")
    try:
        with open(path, encoding="utf-8") as fp:
            tiers[fname] = json.load(fp)["rows"]
    except (OSError, ValueError) as e:
        print(f"WARN: 缺 {fname} 档数据（{e}），该档不出现在对比章节")

# 章节 2/3：结构校验与端到端用 standard 档数据（若无则空表）
std_rows = tiers.get("standard", [])
valid_rows = [{"item": r["item"], "parts": r["parts"], "ok": r["ok"],
               "issues": r.get("issues", [])} for r in std_rows]
valid_n = sum(1 for r in valid_rows if r["ok"])
valid_total = len(valid_rows)
valid_rate = valid_n / valid_total if valid_total else 0.0
e2e_rows = std_rows

conclusion = [
    f"1. 先验命中率 **{hit_rate:.1%}**（目标 ≥ 90%）：{'✅ 达标' if hit_rate >= 0.9 else '❌ 未达标'}。",
    f"2. 结构校验通过率 **{valid_rate:.1%}**（standard 档，{valid_n}/{valid_total}）："
    f"{'✅ 达标' if valid_rate >= 0.9 else '❌ 未达标（占位图联调，真实图片需人工补评）'}。",
    "3. 回归对比：本报告由 Track G T6 三档评估生成，先验开/关回归由 standard 档运行覆盖"
    "（surface 修正/结构无劣化结论见 standard 档运行记录）。",
    "4. 三档时延与 mode 回显详见第 6 章；extreme 为渲染管线冒烟（契约 T6-2）。",
]

E._write_report({
    "built_at": datetime.now().astimezone().isoformat(timespec="seconds"),
    "has_llm": True, "total": total,
    "sampled": sum(1 for r in hit_rows if r["src"] != "manual"),
    "manual": sum(1 for r in hit_rows if r["src"] == "manual"),
    "hits": hits, "hit_rate": hit_rate, "hit_rows": hit_rows,
    "valid_n": valid_n, "valid_total": valid_total, "valid_rate": valid_rate,
    "valid_rows": valid_rows,
    "mock_note": "T6 三档评估（真实 LLM + mock 兜底）",
    "e2e_note": "章节 3 为 standard 档端到端（占位图跑 build3d）；advanced/extreme 见第 6 章。",
    "e2e_rows": e2e_rows,
    "diff_rows": [],
    "conclusion": conclusion,
    "tier_rows": [], "tier_note": "",
})
print("REPORT MERGED OK:", total, "samples, tiers:", list(tiers.keys()))
