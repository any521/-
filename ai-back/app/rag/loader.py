"""app.rag.loader · 单例索引加载器（RAG-02）

- 读取 RAG-01 产物（C1 契约）：`rag_data/index/{meta.json, bm25.pkl, inverted.pkl,
  vector_tf.npz, vector_ids.json, vocab.json, prices.json}` + `rag_data/documents.json` +
  `rag_data/priors_3d.json`（RAG-04 可选产出）。
- mtime/version 感知热重载：meta.json / priors_3d.json 的 mtime+size 变化 → 惰性全量重载。
- 降级语义：索引缺失/加载失败 → `get_state()` 返回 None（或部件为 None），warn 一次不刷屏，
  记 `last_degraded_reason`；检索器据此返回 []/None，不抛异常。
- BM25 引擎三级降级（任务书 §2.2）：langchain-community BM25Retriever → rank_bm25 BM25Okapi
  → 内置倒排 inverted.pkl；TF 向量：scipy 稀疏矩阵 → 现场构建纯 dict 向量。
"""
from __future__ import annotations

import hashlib
import json
import logging
import pickle
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from app.core.logging import get_logger
from app.rag.tokenizer import tokenize

log = get_logger("rag")

_AI_BACK_ROOT = Path(__file__).resolve().parents[2]
_DEFAULT_DATA_DIR = _AI_BACK_ROOT / "rag_data"
_DEFAULT_INDEX_DIR = _DEFAULT_DATA_DIR / "index"

# data_source 可信度序（0 最可信，与 confidence 映射一致，用于价格排序取第一条）
_SOURCE_RANK = {"real_base": 0, "synthesized_from_real_base": 1, "synthetic_estimate": 2}

# pickle 反序列化可执行任意代码，此处文件为可信内部生成。
# 生产环境应校验文件哈希（填入 EXPECTED_HASHES 对应值），留空则跳过校验。
EXPECTED_HASHES = {
    "bm25.pkl": "",       # sha256:<hex>，留空跳过校验；生产环境应填入实际哈希
    "inverted.pkl": "",   # sha256:<hex>，留空跳过校验；生产环境应填入实际哈希
}


def verify_file_hash(filepath, expected_hash):
    """校验文件哈希，防止篡改"""
    if not expected_hash:
        return True  # 未配置预期哈希则跳过校验
    algo, expected = expected_hash.split(":", 1)
    h = hashlib.sha256() if algo == "sha256" else hashlib.md5()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    actual = h.hexdigest()
    if actual != expected:
        raise ValueError(f"文件哈希校验失败: {filepath} (预期 {expected}, 实际 {actual})")
    return True


@dataclass
class _IndexState:
    """进程内索引快照；重载时整体替换，外部只读使用"""

    meta: dict | None = None
    docs: list[dict] = field(default_factory=list)
    bm25: Any | None = None            # BM25Okapi（bm25.pkl 内嵌对象）
    bm25_doc_ids: list | None = None   # bm25.pkl 的 doc_ids（与 documents 顺序对齐校验）
    inverted: dict | None = None       # {word: [[doc_id, tf], ...]}（inverted.pkl）
    keyword_engine: str | None = None  # langchain | rank_bm25 | builtin
    tf_matrix: Any | None = None       # scipy csr_matrix（vector_tf.npz）
    row_norms: Any | None = None       # 行 L2 范数（余弦归一化预计算）
    vector_ids: list | None = None     # 矩阵行 → 文档 id（与 docs 下标对齐）
    vocab: dict | None = None          # 词 → 列号
    dict_vectors: dict | None = None   # 降级向量 {doc_idx: {word: tf}}
    vector_engine: str | None = None   # scipy | dict
    prices: dict | None = None         # {by_name, by_category}
    priors: Any | None = None          # priors_3d.json（RAG-04，可选）

    @property
    def ready(self) -> bool:
        """索引主体就绪（docs 可还原 + 至少一路打分器可用）"""
        if not self.docs:
            return False
        return bool(self.bm25 or self.inverted or self.tf_matrix or self.dict_vectors)


_index_dir: Path = _DEFAULT_INDEX_DIR
_data_dir: Path = _DEFAULT_DATA_DIR
_state: _IndexState | None = None
_state_sig: str | None = ""      # meta.json 签名；""=从未加载（与 None=文件不存在 区分，保证首次必重载）
_priors_sig: str | None = ""     # priors_3d.json 签名；同上
_lock = threading.Lock()
_degraded_warned = False
last_degraded_reason: str | None = None


def _file_sig(path: Path) -> str | None:
    """文件热重载签名：mtime_ns + size；不存在返回 None"""
    try:
        st = path.stat()
        return f"{st.st_mtime_ns}:{st.st_size}"
    except OSError:
        return None


def _warn_degraded(reason: str) -> None:
    """降级告警：记 last_degraded_reason，warn 一次不刷屏（索引恢复后重置标记）"""
    global _degraded_warned, last_degraded_reason
    last_degraded_reason = reason
    if not _degraded_warned:
        log.warning(f"RAG 检索降级: {reason}")
        _degraded_warned = True


def _select_keyword_engine() -> str:
    """BM25 引擎三级降级：langchain-community → rank_bm25 → 内置倒排"""
    try:
        from langchain_community.retrievers import BM25Retriever  # noqa: F401
        return "langchain"
    except Exception:
        pass
    try:
        import rank_bm25  # noqa: F401
        return "rank_bm25"
    except Exception:
        return "builtin"


def _load_keyword(st: _IndexState) -> None:
    """关键词索引：bm25.pkl 优先，inverted.pkl 兜底（兼容 C1 裸对象与 RAG-01 包装格式）"""
    bm25_path = _index_dir / "bm25.pkl"
    if bm25_path.exists():
        try:
            verify_file_hash(bm25_path, EXPECTED_HASHES.get("bm25.pkl"))
            with open(bm25_path, "rb") as f:
                payload = pickle.load(f)
            obj = payload.get("bm25") if isinstance(payload, dict) else payload
            if hasattr(obj, "get_scores"):
                # 能反序列化即说明 rank_bm25 类可用；引擎按导入优先级命名
                st.bm25 = obj
                st.bm25_doc_ids = payload.get("doc_ids") if isinstance(payload, dict) else None
                st.keyword_engine = "langchain" if _select_keyword_engine() == "langchain" else "rank_bm25"
                if st.bm25_doc_ids and len(st.bm25_doc_ids) == len(st.docs):
                    mismatch = sum(1 for a, b in zip(st.bm25_doc_ids,
                                                     (d.get("id") for d in st.docs)) if a != b)
                    if mismatch:
                        log.warning(f"bm25 doc_ids 与 documents 顺序不一致 {mismatch} 处")
        except Exception as exc:
            log.warning(f"bm25.pkl 加载失败: {type(exc).__name__}: {exc}")
    inv_path = _index_dir / "inverted.pkl"
    if inv_path.exists():
        try:
            verify_file_hash(inv_path, EXPECTED_HASHES.get("inverted.pkl"))
            with open(inv_path, "rb") as f:
                payload = pickle.load(f)
            if isinstance(payload, dict):
                # RAG-01 包装格式 {"inverted": {...}} 与 C1 裸 {word: [[doc_id, tf], ...]} 均兼容
                data = payload.get("inverted") if isinstance(payload.get("inverted"), dict) else payload
                if isinstance(data, dict):
                    st.inverted = data
        except Exception as exc:
            log.warning(f"inverted.pkl 加载失败: {type(exc).__name__}: {exc}")
    if st.bm25 is not None:
        return
    if st.inverted is not None:
        st.keyword_engine = "builtin"
        return
    _warn_degraded("关键词索引缺失（bm25.pkl / inverted.pkl 均不可用）")


def _load_vectors(st: _IndexState) -> None:
    """词频向量：scipy 稀疏矩阵（npz）→ 失败降级现场构建纯 dict 向量"""
    try:
        from scipy import sparse
        npz = _index_dir / "vector_tf.npz"
        vids = _index_dir / "vector_ids.json"
        vocab = _index_dir / "vocab.json"
        if npz.exists() and vids.exists() and vocab.exists():
            st.tf_matrix = sparse.load_npz(str(npz)).tocsr()
            st.vector_ids = json.loads(vids.read_text(encoding="utf-8"))
            st.vocab = json.loads(vocab.read_text(encoding="utf-8"))
            if st.tf_matrix.shape[0] > 0 and len(st.vector_ids) == st.tf_matrix.shape[0]:
                import numpy as np
                st.row_norms = np.asarray(st.tf_matrix.multiply(st.tf_matrix).sum(axis=1)).ravel() ** 0.5
                st.vector_engine = "scipy"
                return
            st.tf_matrix = st.vector_ids = st.vocab = st.row_norms = None
    except Exception as exc:
        log.warning(f"TF 稀疏矩阵加载失败，降级 dict 向量: {type(exc).__name__}: {exc}")
    vecs: dict[int, dict[str, int]] = {}
    for i, doc in enumerate(st.docs):
        tf: dict[str, int] = {}
        for w in tokenize(doc.get("text") or ""):
            tf[w] = tf.get(w, 0) + 1
        if tf:
            vecs[i] = tf
    st.dict_vectors = vecs or None
    st.vector_engine = "dict" if vecs else None
    if not vecs:
        _warn_degraded("词频向量构建为空")


def _load_prices(st: _IndexState) -> None:
    """价格查找表（prices.json）；缺失 → price_lookup 返回 None"""
    p = _index_dir / "prices.json"
    if not p.exists():
        _warn_degraded("prices.json 缺失，price_lookup 返回 None")
        return
    try:
        st.prices = json.loads(p.read_text(encoding="utf-8"))
    except Exception as exc:
        _warn_degraded(f"prices.json 解析失败: {type(exc).__name__}: {exc}")


def _load_priors(st: _IndexState) -> None:
    """3D/问答先验（RAG-04 产出，可选）；缺失为合法状态（category_prior 返回 None），不告警"""
    p = _data_dir / "priors_3d.json"
    if not p.exists():
        return
    try:
        st.priors = json.loads(p.read_text(encoding="utf-8"))
    except Exception as exc:
        log.warning(f"priors_3d.json 解析失败: {type(exc).__name__}: {exc}")


def _reload_all() -> None:
    """全量加载索引产物；任何核心缺失 → 状态置 None（检索器返回空），不抛异常"""
    global _state, _state_sig, _priors_sig, _degraded_warned
    meta_sig = _file_sig(_index_dir / "meta.json")
    priors_sig = _file_sig(_data_dir / "priors_3d.json")
    if meta_sig is None:
        if _state is not None:
            _state = None
            _degraded_warned = False  # 允许恢复后重新告警
        _warn_degraded(f"索引缺失: {_index_dir / 'meta.json'}（RAG-01 未交付或索引被清理）")
        _state_sig, _priors_sig = None, priors_sig
        return
    st = _IndexState()
    try:
        st.meta = json.loads((_index_dir / "meta.json").read_text(encoding="utf-8"))
    except Exception as exc:
        _warn_degraded(f"meta.json 解析失败: {type(exc).__name__}: {exc}")
        _state, _state_sig, _priors_sig = None, meta_sig, priors_sig
        return
    try:
        payload = json.loads((_data_dir / "documents.json").read_text(encoding="utf-8"))
        st.docs = payload.get("documents") or []
    except Exception as exc:
        _warn_degraded(f"documents.json 加载失败: {type(exc).__name__}: {exc}")
        _state, _state_sig, _priors_sig = None, meta_sig, priors_sig
        return
    if not st.docs:
        _warn_degraded("documents.json 为空（record_count=0）")
        _state, _state_sig, _priors_sig = None, meta_sig, priors_sig
        return
    _load_keyword(st)
    _load_vectors(st)
    _load_prices(st)
    _load_priors(st)
    _state = st
    _state_sig, _priors_sig = meta_sig, priors_sig
    _degraded_warned = False
    log.info(f"RAG 索引就绪: {st.meta.get('record_count')} 条, "
             f"keyword={st.keyword_engine}, vector={st.vector_engine}")


def get_state() -> _IndexState | None:
    """返回当前索引快照（惰性加载 + mtime/version 感知热重载）；缺失返回 None"""
    global _state_sig, _priors_sig
    meta_sig = _file_sig(_index_dir / "meta.json")
    priors_sig = _file_sig(_data_dir / "priors_3d.json")
    if meta_sig != _state_sig or priors_sig != _priors_sig:
        with _lock:
            if meta_sig != _state_sig or priors_sig != _priors_sig:
                _reload_all()
    return _state


def warmup() -> None:
    """应用启动时预热（main.py startup 调用一次）；失败仅 warn 不阻断启动"""
    try:
        get_state()
        st = _state
        if st is not None and st.ready:
            log.info(f"RAG 索引预热完成: record_count={len(st.docs)}, "
                     f"keyword={st.keyword_engine}, vector={st.vector_engine}")
        else:
            _warn_degraded("rag_data/index 缺失或为空，检索将返回空（RAG-01 交付后自动生效）")
    except Exception as exc:  # 防御：任何异常不阻断启动
        _warn_degraded(f"预热异常: {type(exc).__name__}: {exc}")


def set_index_root(index_root: str | Path) -> None:
    """注入索引根目录（冒烟/测试用，默认 ai-back/rag_data/index）；注入后强制下次重载"""
    global _index_dir, _data_dir, _state_sig, _priors_sig
    _index_dir = Path(index_root)
    _data_dir = _index_dir.parent
    _state_sig = ""
    _priors_sig = ""


def status() -> dict:
    """调试/冒烟状态摘要"""
    st = _state
    return {
        "ready": bool(st is not None and st.ready),
        "record_count": len(st.docs) if st else 0,
        "keyword_engine": st.keyword_engine if st else None,
        "vector_engine": st.vector_engine if st else None,
        "prices": bool(st and st.prices),
        "priors": bool(st and st.priors),
        "last_degraded_reason": last_degraded_reason,
    }
