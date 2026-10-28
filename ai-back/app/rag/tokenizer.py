"""app.rag.tokenizer · 分词器（与 RAG-01 索引构建完全一致，勿改动）

规则：re.findall(r"[一-鿿]+|[A-Za-z0-9]+", text.lower())
- 中文按连续汉字串切；字母数字按连续串切；不引入 jieba（零重依赖，语义够用）
"""
from __future__ import annotations

import re

_PATTERN = re.compile(r"[\u4e00-\u9fff]+|[A-Za-z0-9]+")


def tokenize(text: str) -> list[str]:
    """返回小写 token 列表；空串/None 输入返回 []"""
    if not text:
        return []
    return _PATTERN.findall(str(text).lower())
