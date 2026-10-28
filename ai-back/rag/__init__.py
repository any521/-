"""rag · RAG 数据管道包

- `rag.indexing`（RAG-01 独占）：数据管道与索引构建，产物写 rag_data/。
- `rag.priors`（RAG-04 独占）：3D 先验聚合与降级加载。
注意与 `app.rag`（RAG-02 检索框架）区分：本包自包含，不依赖 RAG-01/02 的任何模块。
"""
