"""app.rag.retriever · C2 契约检索服务（RAG-02，签名冻结于 rag_overview.md §3.2）

五个公开函数：keyword_search / semantic_search / hybrid_search / price_lookup / category_prior
- 排序：score 降序；分数差 < 0.05 时 confidence 高者优先（real_base 恒排最前）
- 过滤：filters（category/region/city/data_source 精确匹配；date_ge/date_le 字符串比较）
- 混合：关键词 + 语义各取 top_k*3 → RRF 融合（Σ 1/(60+rank)）→ ×(0.8+0.2*confidence)
- 降级：索引缺失/依赖缺失 → 返回 []/None，不抛异常（warn 由 loader 统一管理）
- 扩展点：配置 `RAG_EMBEDDING_MODEL` 环境变量后，semantic_search 可切换为 embedding
  向量检索（当前实现为词频向量余弦，零重依赖）
"""
from __future__ import annotations

from functools import cmp_to_key
from typing import Any

from app.core.logging import get_logger
from app.rag import loader, tokenizer

log = get_logger("rag")

# data_source 可信度序（价格查找排序取第一条时 real_base 恒最前）
_SOURCE_RANK = {"real_base": 0, "synthesized_from_real_base": 1, "synthetic_estimate": 2}


# ---------- 内部工具 ----------

def _match_filters(doc: dict, filters: dict | None) -> bool:
    """filters：category/region/city/data_source 精确匹配；date_ge/date_le 字符串比较"""
    if not filters:
        return True
    for key in ("category", "region", "city", "data_source"):
        v = filters.get(key)
        if v is not None and str(doc.get(key) or "") != str(v):
            return False
    for key in ("date_ge", "date_le"):
        v = filters.get(key)
        if v is None:
            continue
        d = str(doc.get("date") or "")
        if not d:
            return False  # 日期缺失/非法 → 日期过滤时跳过（RAG-01 清洗语义）
        if key == "date_ge" and d < str(v):
            return False
        if key == "date_le" and d > str(v):
            return False
    return True


def _cmp(a: dict, b: dict) -> int:
    """score 降序；分数差 < 0.05 视为相近 → confidence 高者优先"""
    diff = a["score"] - b["score"]
    if abs(diff) >= 0.05:
        return -1 if diff > 0 else 1
    if a["confidence"] != b["confidence"]:
        return -1 if a["confidence"] > b["confidence"] else 1
    return -1 if diff > 0 else (1 if diff < 0 else 0)


def _sort_results(results: list[dict]) -> list[dict]:
    return sorted(results, key=cmp_to_key(_cmp))


def _to_retrieved(doc: dict, score: float) -> dict:
    """C1 文档 → C2 RetrievedDoc（字段逐字对齐契约）"""
    return {
        "id": str(doc.get("id") or ""),
        "name": str(doc.get("name") or ""),
        "category": str(doc.get("category") or ""),
        "sub_category": str(doc.get("sub_category") or ""),
        "material": str(doc.get("material") or ""),
        "form": str(doc.get("form") or ""),
        "description": str(doc.get("description") or ""),
        "price": doc.get("price"),
        "min_price": doc.get("min_price"),
        "max_price": doc.get("max_price"),
        "price_unit": str(doc.get("price_unit") or ""),
        "region": str(doc.get("region") or ""),
        "city": str(doc.get("city") or ""),
        "data_source": str(doc.get("data_source") or ""),
        "confidence": float(doc.get("confidence") or 0.0),
        "score": round(float(score), 6),
        "text": str(doc.get("text") or ""),
    }


def _candidate_indexes(st: Any, filters: dict | None, min_confidence: float) -> list[int]:
    """先过滤再打分：候选集 = filters + min_confidence 过滤后的文档下标"""
    if not st.docs:
        return []
    return [
        i for i, d in enumerate(st.docs)
        if float(d.get("confidence") or 0.0) >= min_confidence and _match_filters(d, filters)
    ]


def _entry_date_ok(hit: dict, date: str) -> bool:
    """价格条目日期过滤：date ≥ 参数（字段取 date 优先，其次 base_price_date；无字段视为满足）"""
    d = str(hit.get("date") or hit.get("base_price_date") or "")
    if not d:
        return True
    return d >= str(date)


# ---------- C2 公开函数 ----------

def keyword_search(query: str, top_k: int = 5, filters: dict | None = None,
                   min_confidence: float = 0.0) -> list[dict]:
    """关键词检索：BM25Okapi 打分降序（引擎三级降级，见 loader）；无索引/无词返回 []"""
    if top_k <= 0:
        return []
    st = loader.get_state()
    if st is None or not st.docs:
        return []
    q_tokens = tokenizer.tokenize(query)
    if not q_tokens:
        return []
    cand = _candidate_indexes(st, filters, min_confidence)
    if not cand:
        return []
    if st.bm25 is not None:
        scores = st.bm25.get_scores(q_tokens)
        results = [_to_retrieved(st.docs[i], float(scores[i])) for i in cand if float(scores[i]) > 0]
    elif st.inverted is not None:
        acc: dict[int, float] = {}
        for w in q_tokens:
            for doc_id, tf in st.inverted.get(w, []):
                di = int(doc_id) if not isinstance(doc_id, int) else doc_id
                acc[di] = acc.get(di, 0.0) + float(tf)
        cand_set = set(cand)
        results = [_to_retrieved(st.docs[i], s) for i, s in acc.items() if i in cand_set and s > 0]
    else:
        return []
    return _sort_results(results)[:top_k]


def semantic_search(query: str, top_k: int = 5, filters: dict | None = None,
                    min_confidence: float = 0.0) -> list[dict]:
    """语义检索：词频向量余弦相似度降序（v·q/(|v||q|)）；q 零向量返回 []"""
    if top_k <= 0:
        return []
    st = loader.get_state()
    if st is None or not st.docs:
        return []
    q_tokens = tokenizer.tokenize(query)
    if not q_tokens:
        return []
    cand = _candidate_indexes(st, filters, min_confidence)
    if not cand:
        return []
    q_tf: dict[str, int] = {}
    for w in q_tokens:
        q_tf[w] = q_tf.get(w, 0) + 1
    results: list[dict] = []
    if st.vector_engine == "scipy" and st.tf_matrix is not None:
        cols, data = [], []
        for w, tf in q_tf.items():
            c = (st.vocab or {}).get(w)
            if c is not None:
                cols.append(c)
                data.append(tf)
        if not cols:
            return []  # 查询词全不在词表 → 零向量 → 空
        from scipy import sparse
        q_vec = sparse.csr_matrix((data, ([0] * len(cols), cols)), shape=(1, len(st.vocab)))
        q_norm = (sum(t * t for t in data)) ** 0.5
        dot = st.tf_matrix.dot(q_vec.T).toarray().ravel()
        for i in cand:
            rn = float(st.row_norms[i]) if i < len(st.row_norms) else 0.0
            if rn <= 0 or q_norm <= 0:
                continue
            cos = float(dot[i]) / (rn * q_norm)
            if cos > 0:
                results.append(_to_retrieved(st.docs[i], cos))
    elif st.dict_vectors:
        q_norm = (sum(t * t for t in q_tf.values())) ** 0.5
        if q_norm <= 0:
            return []
        for i in cand:
            dv = st.dict_vectors.get(i)
            if not dv:
                continue
            dot = sum(tf * q_tf.get(w, 0) for w, tf in dv.items())
            if dot <= 0:
                continue
            v_norm = (sum(t * t for t in dv.values())) ** 0.5
            if v_norm <= 0:
                continue
            results.append(_to_retrieved(st.docs[i], dot / (v_norm * q_norm)))
    else:
        return []
    return _sort_results(results)[:top_k]


def hybrid_search(query: str, top_k: int = 5, filters: dict | None = None,
                  min_confidence: float = 0.0) -> list[dict]:
    """混合检索：关键词 + 语义各 top_k*3 → RRF 融合（Σ 1/(60+rank)）→ ×(0.8+0.2*confidence)"""
    if top_k <= 0:
        return []
    st = loader.get_state()
    if st is None or not st.docs:
        return []
    pool = top_k * 3
    kw = keyword_search(query, top_k=pool, filters=filters, min_confidence=min_confidence)
    sem = semantic_search(query, top_k=pool, filters=filters, min_confidence=min_confidence)
    if not kw and not sem:
        return []
    rrf: dict[str, float] = {}
    for rank, item in enumerate(kw, start=1):
        rrf[item["id"]] = rrf.get(item["id"], 0.0) + 1.0 / (60.0 + rank)
    for rank, item in enumerate(sem, start=1):
        rrf[item["id"]] = rrf.get(item["id"], 0.0) + 1.0 / (60.0 + rank)
    id_to_doc = {d.get("id"): d for d in st.docs}
    results: list[dict] = []
    for doc_id, s in rrf.items():
        doc = id_to_doc.get(doc_id)
        if doc is None:
            continue
        confidence = float(doc.get("confidence") or 0.0)
        results.append(_to_retrieved(doc, s * (0.8 + 0.2 * confidence)))
    return _sort_results(results)[:top_k]


def price_lookup(name: str | None = None, category: str | None = None,
                 region: str | None = None, city: str | None = None,
                 date: str | None = None) -> dict | None:
    """价格查找：by_name 精确命中直接返回（仅按 data_source 排序取第一条）
    → 否则 by_category（key 为 sub_category）region 前缀匹配 + date ≥ 过滤 → 否则 None。
    region 参数为空则取该品类第一条（RAG-01 构建时已 real_base 在前，此处再稳排一次）"""
    st = loader.get_state()
    if st is None or not st.prices:
        return None
    prices = st.prices
    hits: list[dict] = []
    if name:
        hits = (prices.get("by_name") or {}).get(name) or []
    if not hits and category:
        hits = (prices.get("by_category") or {}).get(category) or []
    if not hits:
        return None
    # by_name 命中：仅 date 过滤（"直接返回"语义，不做 region/city 二次筛选）
    # by_category 命中：region 前缀匹配（region 或 base_region，real_base 条目 region 为省/base_region 为大区）
    #                   + city 精确匹配（条目有 city 字段才过滤）+ date 过滤
    if name:
        if date:
            hits = [h for h in hits if _entry_date_ok(h, date)]
    else:
        if region:
            r = str(region)
            hits = [h for h in hits
                    if str(h.get("region") or "").startswith(r)
                    or str(h.get("base_region") or "").startswith(r)]
        if city:
            hits = [h for h in hits if not h.get("city") or str(h.get("city")) == str(city)]
        if date:
            hits = [h for h in hits if _entry_date_ok(h, date)]
    if not hits:
        return None
    hits.sort(key=lambda h: _SOURCE_RANK.get(str(h.get("data_source")), 9))
    h = hits[0]
    return {
        "name": str(h.get("name") or ""),
        "category": str(category or h.get("category") or ""),
        "region": str(h.get("region") or ""),
        "min_price": h.get("min_price"),
        "max_price": h.get("max_price"),
        "price_unit": str(h.get("price_unit") or ""),
        "data_source": str(h.get("data_source") or ""),
        "base_region": h.get("base_region"),
        "base_price_date": h.get("base_price_date"),
        "source_url": h.get("source_url"),
    }


def category_prior(category: str) -> dict | None:
    """3D/问答先验：rag_data/priors_3d.json（RAG-04 产出）按 category 精确匹配透传；
    文件不存在/未交付 → None（合法降级，聚合逻辑归 RAG-04）"""
    st = loader.get_state()
    if st is None or not st.priors:
        return None
    priors = st.priors
    if isinstance(priors, dict):
        item = priors.get(category)
        if isinstance(item, dict):
            return dict(item)
        seq = priors.get("priors")
        if isinstance(seq, dict):  # RAG-04 实际产出：{"version", "priors": {sub_category: {...}}}
            item = seq.get(category)
            if isinstance(item, dict):
                return dict(item)
        elif isinstance(seq, list):
            for p in seq:
                if isinstance(p, dict) and p.get("category") == category:
                    return dict(p)
        return None
    if isinstance(priors, list):
        for p in priors:
            if isinstance(p, dict) and p.get("category") == category:
                return dict(p)
    return None
