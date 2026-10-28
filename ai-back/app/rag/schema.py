"""app.rag.schema · C2 契约类型（与 rag_overview.md §3.2 逐字段对齐，冻结勿改）

- 本质为 dict（返回 JSON 兼容），用 TypedDict 声明字段契约，供 RAG-03/04 消费与静态校验
"""
from __future__ import annotations

from typing import Optional, TypedDict


class RetrievedDoc(TypedDict):
    """检索命中文档（keyword/semantic/hybrid 返回元素）"""

    id: str
    name: str
    category: str
    sub_category: str
    material: str
    form: str
    description: str
    price: Optional[float]
    min_price: Optional[float]
    max_price: Optional[float]
    price_unit: str
    region: str
    city: str
    data_source: str
    confidence: float
    score: float
    text: str


class PriceHit(TypedDict):
    """价格查找命中（price_lookup 返回；透传来源字段）"""

    name: str
    category: str
    region: str
    min_price: Optional[float]
    max_price: Optional[float]
    price_unit: str
    data_source: str
    base_region: Optional[str]
    base_price_date: Optional[str]
    source_url: Optional[str]


class CategoryPrior(TypedDict):
    """3D/问答先验（RAG-04 产出，本批次仅加载透传）"""

    category: str
    top_names: list[str]
    forms: list[str]
    materials: list[str]
    suggested_surface: Optional[str]
    count: int
    confidence: float
