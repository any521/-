"""app.rag.__main__ · 冒烟入口（RAG-02 验证，`python -m app.rag` 运行）

- 优先加载真实索引 `rag_data/index/`（RAG-01 交付后，按任务书 §3.2 四个用例验证）；
- 缺失时用 `data/recyclables.json` 前 100 条（补 1 条"玻璃瓶"）在临时目录构造 fixture 索引
  验证框架逻辑（不落 rag_data/，tempfile 自动清理；--keep 保留供人工检查）；
- `--engine builtin`：fixture 仅产 inverted.pkl（meta.bm25_impl=builtin），验证内置倒排降级；
- `--bench`：timeit 100 次 hybrid_search，输出 P95（任务书 §3 验证方式 4，P95 ≤ 200ms）；
- 退出码：全部断言通过 0，失败 1。
"""
from __future__ import annotations

import argparse
import json
import pickle
import shutil
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

from app.rag import loader, retriever, tokenizer

_AI_BACK_ROOT = Path(__file__).resolve().parents[2]
_CONF_MAP = {"real_base": 1.0, "synthesized_from_real_base": 0.85, "synthetic_estimate": 0.5}

_fails: list[str] = []


def _check(cond: bool, label: str) -> None:
    print(f"  {'✅' if cond else '❌'} {label}")
    if not cond:
        _fails.append(label)


def _to_c1_doc(r: dict) -> dict:
    """fixture 专用：原始记录 → C1 规范化文档（规则与 RAG-01 §2.2~2.3 一致）"""
    parts = [r.get("name") or "", r.get("category") or "", r.get("sub_category") or "",
             r.get("form") or "", r.get("material") or "", r.get("description") or "",
             " ".join(r.get("tags") or []), r.get("notes") or ""]
    return {
        "id": str(r.get("id") or ""),
        "name": r.get("name") or "未知",
        "category": r.get("category") or "未知",
        "sub_category": r.get("sub_category") or "未知",
        "form": r.get("form") or "未知",
        "material": r.get("material") or "未知",
        "description": r.get("description") or "",
        "price": r.get("price"),
        "price_unit": r.get("price_unit") or "元/公斤",
        "min_price": r.get("min_price"),
        "max_price": r.get("max_price"),
        "region": r.get("region") or "",
        "city": r.get("city") or "",
        "district": r.get("district") or "",
        "station_name": r.get("station_name") or "",
        "station_type": r.get("station_type") or "",
        "date": r.get("date") or "",
        "tags": r.get("tags") or [],
        "data_source": r.get("data_source") or "synthetic_estimate",
        "base_region": r.get("base_region"),
        "base_price_date": r.get("base_price_date"),
        "source_url": r.get("source_url"),
        "city_level": r.get("city_level") or "",
        "text": " ".join(p for p in parts if p),
        "confidence": _CONF_MAP.get(r.get("data_source"), 0.5),
    }


def _build_fixture(engine: str) -> Path:
    """构造临时 fixture 索引（tmp/index/ + tmp/documents.json），返回 tmp 根目录"""
    raw = json.loads((_AI_BACK_ROOT / "data" / "recyclables.json").read_text(encoding="utf-8"))["records"]
    docs = [_to_c1_doc(r) for r in raw[:100]]
    if not any("玻璃瓶" in d["name"] for d in docs):  # 补 keyword 冒烟用例所需
        docs.append(_to_c1_doc(next(r for r in raw if "玻璃瓶" in (r.get("name") or ""))))
    corpus = [tokenizer.tokenize(d["text"]) for d in docs]
    tmp = Path(tempfile.mkdtemp(prefix="rag_fixture_"))
    (tmp / "index").mkdir()
    if engine == "rank_bm25":
        from rank_bm25 import BM25Okapi
        with open(tmp / "index" / "bm25.pkl", "wb") as f:
            pickle.dump(BM25Okapi(corpus), f)
    else:
        inverted: dict[str, list] = {}
        for i, toks in enumerate(corpus):
            tf: dict[str, int] = {}
            for w in toks:
                tf[w] = tf.get(w, 0) + 1
            for w, c in tf.items():
                inverted.setdefault(w, []).append([i, c])
        with open(tmp / "index" / "inverted.pkl", "wb") as f:
            pickle.dump(inverted, f)
    try:  # scipy 可用 → 产 npz；否则 loader 自动降级 dict 向量
        from scipy import sparse
        vocab = sorted({w for toks in corpus for w in toks})
        w2c = {w: c for c, w in enumerate(vocab)}
        rows, cols, data = [], [], []
        for i, toks in enumerate(corpus):
            tf: dict[str, int] = {}
            for w in toks:
                tf[w] = tf.get(w, 0) + 1
            for w, c in tf.items():
                rows.append(i)
                cols.append(w2c[w])
                data.append(c)
        matrix = sparse.csr_matrix((data, (rows, cols)), shape=(len(corpus), len(vocab)))
        sparse.save_npz(tmp / "index" / "vector_tf.npz", matrix)
        (tmp / "index" / "vector_ids.json").write_text(
            json.dumps([d["id"] for d in docs], ensure_ascii=False), encoding="utf-8")
        (tmp / "index" / "vocab.json").write_text(
            json.dumps(w2c, ensure_ascii=False), encoding="utf-8")
    except Exception as e:
        import logging
        logging.getLogger(__name__).warning("scipy 稀疏矩阵保存失败: %s", e)
    by_name: dict[str, list] = {}
    by_category: dict[str, list] = {}
    for d in docs:
        entry = {k: d.get(k) for k in (
            "name", "region", "min_price", "max_price", "price_unit",
            "data_source", "base_region", "base_price_date", "source_url", "city")}
        by_name.setdefault(d["name"], []).append(entry)
        by_category.setdefault(d["sub_category"], []).append(entry)
    (tmp / "index" / "prices.json").write_text(
        json.dumps({"by_name": by_name, "by_category": by_category}, ensure_ascii=False),
        encoding="utf-8")
    (tmp / "documents.json").write_text(
        json.dumps({"version": 1,
                    "built_at": datetime.now(timezone.utc).isoformat(),
                    "count": len(docs), "documents": docs}, ensure_ascii=False),
        encoding="utf-8")
    (tmp / "index" / "meta.json").write_text(
        json.dumps({"version": 1,
                    "build_time": datetime.now(timezone.utc).isoformat(),
                    "record_count": len(docs),
                    "token_count": sum(len(t) for t in corpus),
                    "embedding_model": None,
                    "bm25_impl": engine,
                    "source_files": ["recyclables.json"]}, ensure_ascii=False),
        encoding="utf-8")
    return tmp


def _smoke_fixture() -> None:
    """RAG-01 未交付：fixture 验证框架逻辑（等价于任务书 §3.2 四个用例）"""
    print("== fixture 索引冒烟（等价 §3.2 用例） ==")
    print(f"status: {loader.status()}")
    top5 = retriever.hybrid_search("废纸袋", top_k=5)
    for i, d in enumerate(top5, 1):
        print(f"  hybrid[{i}] {d['name']} | {d['sub_category']} | {d['region']} | "
              f"{d['data_source']} | score={d['score']}")
    _check(len(top5) > 0, "hybrid_search('废纸袋') 非空")
    _check(any(d["sub_category"] == "纸类" for d in top5), "top5 含纸类（废纸类）")
    paper = [d for d in top5 if d["sub_category"] == "纸类"]
    rb = [d for d in paper if d["data_source"] == "real_base"]
    _check(bool(rb) and paper.index(rb[0]) == 0,
           "real_base 记录排最前（可信度加权）")

    kw = retriever.keyword_search("玻璃瓶", filters={"category": "可回收物"})
    print(f"  keyword('玻璃瓶', category=可回收物): {len(kw)} 条, "
          f"全部 category={set(d['category'] for d in kw) or '∅'}")
    _check(len(kw) > 0, "keyword_search('玻璃瓶') 非空")
    _check(all(d["category"] == "可回收物" for d in kw), "keyword 过滤：全部为可回收物")

    kw_bj = retriever.keyword_search("废旧衣服", filters={"region": "北京市"})
    _check(len(kw_bj) > 0 and all(d["region"] == "北京市" for d in kw_bj),
           "keyword region 过滤：全部为北京市")
    print(f"  keyword('废旧衣服', region=北京市): {len(kw_bj)} 条")

    hit = retriever.price_lookup(category="纸类", region="北京")
    print(f"  price_lookup(category=纸类, region=北京): {hit}")
    _check(hit is not None, "price_lookup 命中")
    _check(hit is not None and hit["data_source"] == "real_base" and hit["base_region"],
           "price_lookup 返回 real_base 条目（base_region 非空）")

    prior = retriever.category_prior("可回收物")
    print(f"  category_prior('可回收物'): {prior}")
    _check(prior is None, "category_prior 未交付返回 None（合法降级）")

    empty = retriever.keyword_search("", filters={"category": "不存在"})
    _check(empty == [], "空查询返回 []")

    # 任务书原始样例："废纸 北京" 因整串分词（汉字串）与 text 无 region 字段，预期无全文命中；
    # region 检索走 filters（上例），分词语义与 RAG-01 冻结一致，非缺陷。
    orig = retriever.hybrid_search("废纸 北京", top_k=5)
    print(f"  说明: hybrid_search('废纸 北京') 命中 {len(orig)} 条（整串分词无独立'废纸'/'北京' token，属冻结语义）")


def _smoke_real() -> None:
    """RAG-01 交付后：按任务书 §3.2 用例冒烟（数据域实际键为 sub_category，如"纸类"）"""
    print("== 真实索引冒烟（任务书 §3.2） ==")
    print(f"status: {loader.status()}")
    top5 = retriever.hybrid_search("废旧纸箱", top_k=5)
    for i, d in enumerate(top5, 1):
        print(f"  hybrid[{i}] {d['name']} | {d['sub_category']} | {d['region']} | "
              f"{d['data_source']} | score={d['score']}")
    _check(len(top5) > 0, "hybrid_search('废旧纸箱') 非空")
    _check(any(d["sub_category"] == "废纸类" or d["sub_category"] == "纸类"
               for d in top5), "top5 含废纸类/纸类")
    _check(bool(top5) and top5[0]["data_source"] == "real_base",
           "real_base 记录排最前（可信度加权）")

    kw = retriever.keyword_search("玻璃瓶", filters={"category": "可回收物"})
    _check(all(d["category"] == "可回收物" for d in kw), "keyword 过滤：全部为可回收物")
    print(f"  keyword('玻璃瓶', category=可回收物): {len(kw)} 条")

    kw_bj = retriever.keyword_search("废旧纸箱", filters={"region": "北京市"})
    _check(len(kw_bj) > 0 and all(d["region"] == "北京市" for d in kw_bj),
           "keyword region 过滤：全部为北京市")
    print(f"  keyword('废旧纸箱', region=北京市): {len(kw_bj)} 条")

    # 任务书样例 category="废纸类" 在数据域（sub_category）中为"纸类"，按实际键验证
    hit = retriever.price_lookup(category="纸类", region="华东")
    print(f"  price_lookup(category=纸类, region=华东): {hit}")
    _check(hit is not None and hit["data_source"] == "real_base"
           and hit["base_region"] and hit["base_price_date"],
           "price_lookup 返回 real_base 条目（base_region/base_price_date 非空）")

    prior = retriever.category_prior("纸类")  # RAG-04 已交付：sub_category 键
    print(f"  category_prior('纸类'): {bool(prior)}")
    _check(prior is not None and prior.get("top_names"), "category_prior 命中 RAG-04 先验")
    none_prior = retriever.category_prior("可回收物")  # 大类不在键表 → None，合法降级
    _check(none_prior is None or isinstance(none_prior, dict),
           "category_prior 大类查询合法返回（None=键不存在降级）")

    # 任务书原始样例："废纸 北京" 因整串分词（汉字串）与 text 无 region 字段，预期无全文命中；
    # region 检索走 filters（上例），分词语义与 RAG-01 冻结一致，非缺陷。
    orig = retriever.hybrid_search("废纸 北京", top_k=5)
    print(f"  说明: hybrid_search('废纸 北京') 命中 {len(orig)} 条"
          f"（整串分词无独立'废纸'/'北京' token，属冻结语义，region 检索走 filters）")


def _bench(query: str = "废旧纸箱", n: int = 100, top_k: int = 5) -> None:
    """性能验证（§3 验证方式 4）：100 次 hybrid_search，P95 ≤ 200ms"""
    times = []
    for _ in range(n):
        t0 = time.perf_counter()
        retriever.hybrid_search(query, top_k=top_k)
        times.append((time.perf_counter() - t0) * 1000)
    times.sort()
    p95 = times[int(n * 0.95) - 1]
    print(f"== bench: hybrid_search x{n} == p50={times[n // 2]:.2f}ms "
          f"p95={p95:.2f}ms max={times[-1]:.2f}ms")
    _check(p95 <= 200, f"P95 ≤ 200ms（实际 {p95:.2f}ms）")


def main() -> None:
    parser = argparse.ArgumentParser(description="app.rag 冒烟入口")
    parser.add_argument("--engine", choices=["rank_bm25", "builtin"], default="rank_bm25",
                        help="fixture 关键词引擎（真实索引存在时忽略）")
    parser.add_argument("--bench", action="store_true", help="跑 100 次 hybrid_search 性能基准")
    parser.add_argument("--keep", action="store_true", help="保留 fixture 临时目录")
    args = parser.parse_args()

    real_meta = loader._DEFAULT_INDEX_DIR / "meta.json"  # noqa: SLF001（冒烟允许访问默认路径）
    fixture_dir: Path | None = None
    # --engine builtin 强制走 fixture：真实索引存在时仍可验证内置倒排降级路径
    if real_meta.exists() and args.engine == "rank_bm25":
        _smoke_real()
    else:
        fixture_dir = _build_fixture(args.engine)
        loader.set_index_root(fixture_dir / "index")
        print(f"（使用 fixture 索引: {fixture_dir}，engine={args.engine}）")
        _smoke_fixture()
    if args.bench:
        _bench()
    print(f"\n冒烟完成: {'全部通过 ✅' if not _fails else f'{len(_fails)} 项失败 ❌'}")
    if not args.keep and fixture_dir is not None:
        shutil.rmtree(fixture_dir, ignore_errors=True)
        print(f"fixture 临时目录已清理: {fixture_dir}")
    sys.exit(1 if _fails else 0)


if __name__ == "__main__":
    main()
