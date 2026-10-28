"""rag.priors · 3D 生成先验（统计聚合 + 降级读取）

- build_priors.py：18054 条数据 → rag_data/priors_3d.json（`python -m rag.priors.build_priors`）
- loader.py：RAG-02 未交付时 build3d_service 直接读取先验的降级入口（文件域隔离，自实现 load/clean）
"""
