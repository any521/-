"""app.rag · RAG 检索框架（RAG-02）

- retriever.py 提供 C2 契约的五个公开函数（keyword/semantic/hybrid/price_lookup/category_prior）
- 索引产物由 RAG-01 构建于 rag_data/index/，loader.py 单例加载 + mtime 感知热重载
- 全部函数带降级：无索引/缺依赖时返回空列表/None，不抛异常
"""
