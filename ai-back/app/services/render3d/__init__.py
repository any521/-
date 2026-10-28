# ============================================================
# render3d —— Track G · T4 extreme 渲染闭环（方案 B：服务端渲染截图对比）
# - renderer.py：Playwright headless Chromium 渲染 spec → 3 张 PNG（bytes）；
# - extreme_refine.py：渲染→VLM 对比（fidelity）→修正 闭环，≤3 轮（契约 C4-4）；
#   Playwright/Chromium 不可用 → (spec, "advanced") 降级（契约 C4-3/5），
#   由 build3d_service 挂载点接收实际模式回显。
# 渲染页只消费 JSON spec（数据驱动，与前端 specModel.ts 同一执行器语义），
# 不执行任何 LLM 生成的代码，无代码注入面。
# ============================================================
from .extreme_refine import extreme_refine  # noqa: F401（build3d 挂载点入口）
from .renderer import RenderUnavailable, render_spec  # noqa: F401（渲染设施复用入口）

__all__ = ["extreme_refine", "render_spec", "RenderUnavailable"]
