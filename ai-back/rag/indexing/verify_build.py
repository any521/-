"""rag.indexing.verify_build · RAG-01 产物验证（批次文档 §3 验证方式落地）

运行：cd ai-back && python -m rag.indexing.verify_build
检查项：记录数 / C1 字段抽样 / data_source→confidence 映射 / meta 字段 / 价格表 real_base。
任一失败 exit 1；全部通过打印 SUMMARY 并 exit 0。
"""
from __future__ import annotations

import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "rag_data"
IDX_DIR = OUT_DIR / "index"

CONFIDENCE = {"real_base": 1.0, "synthesized_from_real_base": 0.85, "synthetic_estimate": 0.5}
EXPECT_COUNT = 18054

failures: list[str] = []


def check(cond: bool, msg: str) -> None:
    print(("PASS" if cond else "FAIL") + "  " + msg)
    if not cond:
        failures.append(msg)


def main() -> None:
    docs = json.loads((OUT_DIR / "documents.json").read_text(encoding="utf-8"))
    check(docs["count"] == EXPECT_COUNT, f"documents.count == {EXPECT_COUNT}（实际 {docs['count']}）")
    check(len(docs["documents"]) == EXPECT_COUNT, f"documents 数组长度 == {EXPECT_COUNT}")

    # C1 字段抽样：WR000001 与契约示例对齐
    by_id = {d["id"]: d for d in docs["documents"]}
    first = by_id.get("WR000001")
    check(first is not None, "WR000001 存在")
    if first:
        check(first["name"] == "废旧衣服", f"WR000001.name == 废旧衣服（实际 {first['name']}）")
        check(first["category"] == "可回收物", "WR000001.category == 可回收物")
        check(first["confidence"] == CONFIDENCE[first["data_source"]],
              f"WR000001 confidence 与 data_source 映射一致（{first['data_source']}→{first['confidence']}）")
        check(bool(first["text"]), "WR000001.text 非空")
        check("text" in first and "confidence" in first and "data_source" in first,
              "WR000001 含 C1 关键字段（text/confidence/data_source）")

    # 随机抽 10 条核对 confidence 映射 + 必填字段
    random.seed(42)
    sample = random.sample(docs["documents"], 10)
    for d in sample:
        if d["confidence"] != CONFIDENCE[d["data_source"]]:
            check(False, f"confidence 映射错误 {d['id']} {d['data_source']}")
            break
    else:
        check(True, "随机 10 条 confidence 映射全部正确")
    missing_fields = [d["id"] for d in sample
                      if not all(k in d for k in ("id", "name", "category", "sub_category",
                                                  "form", "material", "description", "price",
                                                  "min_price", "max_price", "region", "city",
                                                  "date", "tags", "data_source", "text", "confidence"))]
    check(not missing_fields, "随机 10 条 C1 字段齐全")

    # meta.json
    meta = json.loads((IDX_DIR / "meta.json").read_text(encoding="utf-8"))
    for k in ("version", "build_time", "record_count", "token_count", "embedding_model",
              "bm25_impl", "vector_impl", "source_files"):
        check(k in meta, f"meta 含字段 {k}")
    check(meta["record_count"] == EXPECT_COUNT, "meta.record_count == 18054")
    check(meta["bm25_impl"] in ("rank_bm25", "builtin"), f"meta.bm25_impl 合法（{meta['bm25_impl']}）")
    check(meta["vector_impl"] in ("scipy", "builtin"), f"meta.vector_impl 合法（{meta['vector_impl']}）")

    # 价格表：真实数据 sub_category 为「纸类」（业务枚举「废纸类」），以数据为准验证
    prices = json.loads((IDX_DIR / "prices.json").read_text(encoding="utf-8"))
    wz = prices["by_category"].get("纸类") or []
    check(len(wz) > 0, f"by_category 含「纸类」（{len(wz)} 条）")
    rb = [e for e in wz if e["data_source"] == "real_base"]
    check(len(rb) > 0, "纸类含 real_base 条目")
    if rb:
        check(bool(rb[0].get("base_region")) and bool(rb[0].get("base_price_date")),
              "real_base 条目 base_region/base_price_date 非空")
    check(len(prices["by_name"]) > 0, f"by_name 非空（{len(prices['by_name'])} 个物品名）")
    # 排序校验：同列表内 real_base 恒在 synthetic 前
    order_ok = all(
        [e["data_source"] for e in lst].index("real_base") <
        [e["data_source"] for e in lst].index("synthetic_estimate")
        for lst in [prices["by_category"].get("纸类") or []]
        if "real_base" in [e["data_source"] for e in lst]
        and "synthetic_estimate" in [e["data_source"] for e in lst])
    check(order_ok, "价格表排序：real_base 排在 synthetic_estimate 前")

    # stats.json
    stats = json.loads((OUT_DIR / "stats.json").read_text(encoding="utf-8"))
    check(sum(stats["data_source_dist"].values()) == EXPECT_COUNT, "stats.data_source_dist 合计 18054")
    check(0 <= stats["price_field_rate"] <= 1, "stats.price_field_rate 在 0-1")

    print("\n" + ("SUMMARY: FAIL " + str(len(failures)) + " 项失败" if failures else "SUMMARY: 全部通过"))
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
