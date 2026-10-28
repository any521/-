"""rag.indexing.build_index · RAG-01 数据管道与索引构建（契约 C1，见 docx/tasks/rag_batch01_data_index.md）

解析 data/*.json → 清洗 → C1 规范化文档 → 三类索引（BM25 / 词频向量 / 价格查找表）→ 持久化 rag_data/。

运行：cd ai-back && python -m rag.indexing.build_index
产物：
- rag_data/documents.json      C1 规范化文档（version/built_at/count/documents）
- rag_data/index/meta.json     元信息（version 自增 / bm25_impl / vector_impl）
- rag_data/index/bm25.pkl      或 inverted.pkl（离线降级）：{doc_ids, corpus|inverted}
- rag_data/index/vector_tf.npz 或 vector_tf.json（降级）：csr 词频矩阵 + vector_ids.json + vocab.json
- rag_data/index/prices.json   价格查找表（by_category / by_name）
- rag_data/stats.json          统计报告

设计约束：data/ 只读；零业务依赖（不 import app.*）；可幂等重跑（version 自增）。
"""
from __future__ import annotations

import json
import logging
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

# 项目根（ai-back/）
ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT / "data"
OUT_DIR = ROOT / "rag_data"
IDX_DIR = OUT_DIR / "index"

SOURCE_FILES = ["recyclables.json", "hazardous.json", "kitchen.json",
                "other.json", "bulky.json", "e_waste.json"]

# data_source → 可信度（C1 冻结）
CONFIDENCE = {"real_base": 1.0, "synthesized_from_real_base": 0.85, "synthetic_estimate": 0.5}
_VALID_DATA_SOURCE = set(CONFIDENCE)

_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
# 与 RAG-02 app/rag/tokenizer.py 必须保持一致（契约）
_TOKEN_RE = re.compile(r"[\u4e00-\u9fff]+|[A-Za-z0-9]+")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("rag.build_index")


# ---------------------------------------------------------------- 加载与清洗
def load_raw() -> list[dict]:
    """读取 6 个分类 JSON（{category, count, records}），平铺为记录列表。"""
    records: list[dict] = []
    for fname in SOURCE_FILES:
        path = DATA_DIR / fname
        if not path.exists():
            log.error("数据文件缺失: %s", path)
            sys.exit(1)
        with open(path, "r", encoding="utf-8-sig") as f:  # 兼容 BOM
            payload = json.load(f)
        recs = payload.get("records") or []
        log.info("load %s: %d 条 (category=%s)", fname, len(recs), payload.get("category"))
        records.extend(recs)
    log.info("合计加载 %d 条", len(records))
    return records


def _as_float(v) -> float | None:
    if v is None or v == "":
        return None
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def clean(rec: dict) -> dict:
    """字段清洗（决策见批次文档 §2.2）。"""
    def s(key: str, default: str = "") -> str:
        v = rec.get(key)
        return str(v).strip() if v is not None else default

    c = {
        "id": s("id"),
        "name": s("name", "未知"),
        "category": s("category", "未知"),
        "sub_category": s("sub_category", "未知"),
        "form": s("form", "未知"),
        "material": s("material", "未知"),
        "description": s("description"),
        "notes": s("notes"),
        "station_name": s("station_name"),
        "station_type": s("station_type"),
        "address": s("address"),
        "contact": s("contact"),
        "business_hours": s("business_hours"),
        "region": s("region"),
        "city": s("city"),
        "district": s("district"),
        "city_level": s("city_level"),
        "price_unit": s("price_unit", "元/公斤"),
        "source": s("source"),
        "base_region": rec.get("base_region"),
        "base_price_date": rec.get("base_price_date"),
        "source_url": rec.get("source_url"),
    }
    # 价格字段 float 化，非法置 null；min>max 交换
    price, min_p, max_p = _as_float(rec.get("price")), _as_float(rec.get("min_price")), _as_float(rec.get("max_price"))
    if min_p is not None and max_p is not None and min_p > max_p:
        min_p, max_p = max_p, min_p
    c["price"], c["min_price"], c["max_price"] = price, min_p, max_p
    # 日期校验：非法置空（date 过滤时跳过）
    d = s("date")
    c["date"] = d if _DATE_RE.match(d) else ""
    # tags 归一化
    tags = rec.get("tags")
    c["tags"] = [str(t).strip() for t in tags] if isinstance(tags, list) else []
    # data_source 归一化
    ds = s("data_source")
    c["data_source"] = ds if ds in _VALID_DATA_SOURCE else "synthetic_estimate"
    return c


# ---------------------------------------------------------------- 规范化文档
def to_document(rec: dict) -> dict:
    """C1 规范化文档（字段逐字对齐总览 §3.1；id 缺失的条目由调用方过滤）。"""
    text_parts = [rec["name"], rec["category"], rec["sub_category"], rec["form"],
                  rec["material"], rec["description"]]
    if rec["tags"]:
        text_parts.append(" ".join(rec["tags"]))
    if rec["notes"]:
        text_parts.append(rec["notes"])
    doc = {
        "id": rec["id"],
        "name": rec["name"],
        "category": rec["category"],
        "sub_category": rec["sub_category"],
        "form": rec["form"],
        "material": rec["material"],
        "description": rec["description"],
        "price": rec["price"], "price_unit": rec["price_unit"],
        "min_price": rec["min_price"], "max_price": rec["max_price"],
        "region": rec["region"], "city": rec["city"], "district": rec["district"],
        "station_name": rec["station_name"], "station_type": rec["station_type"],
        "date": rec["date"],
        "tags": rec["tags"],
        "data_source": rec["data_source"],
        "base_region": rec["base_region"],
        "base_price_date": rec["base_price_date"],
        "source_url": rec["source_url"],
        "city_level": rec["city_level"],
        "text": " ".join(p for p in text_parts if p),
        "confidence": CONFIDENCE[rec["data_source"]],
    }
    return doc


# ---------------------------------------------------------------- 分词与索引
def tokenize(text: str) -> list[str]:
    """与 RAG-02 app/rag/tokenizer.py 一致：汉字串|字母数字串，lower。"""
    return _TOKEN_RE.findall(text.lower())


def _data_source_rank(ds: str) -> int:
    return {"real_base": 0, "synthesized_from_real_base": 1, "synthetic_estimate": 2}[ds]


def build_bm25(docs: list[dict]) -> dict:
    """BM25 索引。rank_bm25 可用 → bm25.pkl{doc_ids, corpus}；不可用 → inverted.pkl 降级。"""
    corpus = [tokenize(d["text"]) for d in docs]
    try:
        from rank_bm25 import BM25Okapi  # type: ignore
        bm25 = BM25Okapi(corpus)
        impl = "rank_bm25"
        payload = {"doc_ids": [d["id"] for d in docs], "bm25": bm25}
        (IDX_DIR / "bm25.pkl").write_bytes(_pickle(payload))
        log.info("BM25 构建完成 impl=rank_bm25 docs=%d", len(docs))
    except Exception as e:  # 离线降级：自实现倒排（决策见批次文档 §2.4）
        log.warning("rank_bm25 不可用（%s），降级自实现倒排", e)
        impl = "builtin"
        inverted: dict[str, list[list]] = {}
        for i, toks in enumerate(corpus):
            tf: dict[str, int] = {}
            for t in toks:
                tf[t] = tf.get(t, 0) + 1
            for t, n in tf.items():
                inverted.setdefault(t, []).append([i, n])
        (IDX_DIR / "inverted.pkl").write_bytes(_pickle({"inverted": inverted}))
        log.info("倒排构建完成 impl=builtin words=%d", len(inverted))
    return {"bm25_impl": impl, "token_count": sum(len(c) for c in corpus)}


def build_tf_vectors(docs: list[dict]) -> str:
    """词频稀疏矩阵。scipy 可用 → vector_tf.npz + vector_ids.json + vocab.json；否则 dict 降级。"""
    corpus = [tokenize(d["text"]) for d in docs]
    vocab: dict[str, int] = {}
    rows: list[list[tuple[int, int]]] = []
    for toks in corpus:
        tf: dict[str, int] = {}
        for t in toks:
            tf[t] = tf.get(t, 0) + 1
        entries = []
        for t, n in tf.items():
            col = vocab.setdefault(t, len(vocab))
            entries.append((col, n))
        rows.append(entries)
    (IDX_DIR / "vector_ids.json").write_text(
        json.dumps([d["id"] for d in docs], ensure_ascii=False), encoding="utf-8")
    (IDX_DIR / "vocab.json").write_text(
        json.dumps(vocab, ensure_ascii=False), encoding="utf-8")
    try:
        from scipy import sparse  # type: ignore
        row_idx, col_idx, data = [], [], []
        for i, entries in enumerate(rows):
            for col, n in entries:
                row_idx.append(i)
                col_idx.append(col)
                data.append(n)
        mat = sparse.csr_matrix((data, (row_idx, col_idx)), shape=(len(docs), len(vocab)))
        sparse.save_npz(IDX_DIR / "vector_tf.npz", mat)
        impl = "scipy"
    except Exception as e:  # 离线降级：{doc_index: {word: tf}}
        log.warning("scipy 不可用（%s），降级 dict 序列化", e)
        (IDX_DIR / "vector_tf.json").write_text(
            json.dumps([dict(entries) for entries in rows], ensure_ascii=False), encoding="utf-8")
        impl = "builtin"
    log.info("词频向量构建完成 impl=%s docs=%d vocab=%d", impl, len(docs), len(vocab))
    return impl


def build_price_index(docs: list[dict]) -> dict:
    """价格查找表：by_category（sub_category 值）与 by_name；仅价格字段齐全的条目。"""
    by_category: dict[str, list[dict]] = {}
    by_name: dict[str, list[dict]] = {}
    skipped = 0
    for d in docs:
        if d["min_price"] is None or d["max_price"] is None or d["price"] is None:
            skipped += 1
            continue
        entry = {
            "name": d["name"], "region": d["region"], "city": d["city"],
            "min_price": d["min_price"], "max_price": d["max_price"],
            "price_unit": d["price_unit"], "data_source": d["data_source"],
            "base_region": d["base_region"], "base_price_date": d["base_price_date"],
            "source_url": d["source_url"], "date": d["date"],
        }
        by_category.setdefault(d["sub_category"], []).append(entry)
        by_name.setdefault(d["name"], []).append(entry)
    for lst in list(by_category.values()) + list(by_name.values()):
        lst.sort(key=lambda e: (_data_source_rank(e["data_source"]), e["date"] or "", e["region"]))
    log.info("价格查找表构建完成 categories=%d names=%d (跳过无价格 %d 条)",
             len(by_category), len(by_name), skipped)
    return {"by_category": by_category, "by_name": by_name, "skipped_no_price": skipped}


# ---------------------------------------------------------------- 统计
def build_stats(docs: list[dict], prices: dict) -> dict:
    by_cat: dict[str, int] = {}
    by_sub: dict[str, int] = {}
    ds_dist: dict[str, int] = {}
    price_ok = 0
    for d in docs:
        by_cat[d["category"]] = by_cat.get(d["category"], 0) + 1
        by_sub[d["sub_category"]] = by_sub.get(d["sub_category"], 0) + 1
        ds_dist[d["data_source"]] = ds_dist.get(d["data_source"], 0) + 1
        if d["price"] is not None:
            price_ok += 1
    real_base_subs = sorted({d["sub_category"] for d in docs if d["data_source"] == "real_base"})
    return {
        "by_category": dict(sorted(by_cat.items())),
        "by_sub_category": dict(sorted(by_sub.items(), key=lambda kv: -kv[1])),
        "data_source_dist": dict(sorted(ds_dist.items())),
        "price_field_rate": round(price_ok / len(docs), 4),
        "real_base_sub_categories": real_base_subs,
        "price_entries": len(prices["by_name"]),
    }


def _pickle(obj) -> bytes:
    import pickle
    return pickle.dumps(obj, protocol=4)


# ---------------------------------------------------------------- 主流程
def build() -> None:
    t0 = time.perf_counter()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    IDX_DIR.mkdir(parents=True, exist_ok=True)

    raw = load_raw()
    docs = []
    skipped_no_id = 0
    for rec in raw:
        c = clean(rec)
        if not c["id"]:
            skipped_no_id += 1
            continue
        docs.append(to_document(c))
    if skipped_no_id:
        log.warning("跳过无 id 记录 %d 条", skipped_no_id)

    # meta.version 自增（幂等重跑）
    version = 1
    old_meta = IDX_DIR / "meta.json"
    if old_meta.exists():
        try:
            version = int(json.loads(old_meta.read_text(encoding="utf-8")).get("version", 0)) + 1
        except Exception:
            version = 1

    # documents.json（C1 契约）
    (OUT_DIR / "documents.json").write_text(
        json.dumps({"version": 1, "built_at": datetime.now(timezone.utc).isoformat(),
                    "count": len(docs), "documents": docs},
                   ensure_ascii=False, indent=1),
        encoding="utf-8")
    log.info("documents.json 写入 %d 条", len(docs))

    # 三类索引
    bm25_info = build_bm25(docs)
    vector_impl = build_tf_vectors(docs)
    prices = build_price_index(docs)
    (IDX_DIR / "prices.json").write_text(
        json.dumps(prices, ensure_ascii=False), encoding="utf-8")

    # meta.json
    meta = {
        "version": version,
        "build_time": datetime.now(timezone.utc).isoformat(),
        "record_count": len(docs),
        "token_count": bm25_info["token_count"],
        "embedding_model": None,
        "bm25_impl": bm25_info["bm25_impl"],
        "vector_impl": vector_impl,
        "source_files": SOURCE_FILES,
    }
    (IDX_DIR / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1), encoding="utf-8")

    # stats.json
    stats = build_stats(docs, prices)
    (OUT_DIR / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")

    log.info("构建完成 version=%d count=%d elapsed=%.1fs → %s",
             version, len(docs), time.perf_counter() - t0, OUT_DIR)


if __name__ == "__main__":
    build()
