"""render3d.extreme_refine · extreme 模式渲染截图对比闭环（Track G · T4 / B4）

流程：advanced 自纠收敛后的 spec → 服务端渲染 3 视角截图（front/three-quarter/top）
→ 审阅（原图 + 截图，含 fidelity）→ 未收敛则修正 → 重渲染，≤3 轮。

契约（trackG_overview.md C4 / trackG_3d_quality_tiers.md T4-2）：
- 失败不阻断：任何 mock/异常保留当前 spec 正常返回；
- 渲染设施不可用 → (原 spec, "advanced")（调用方回显降级 + degraded=true）；
- 收敛：fidelity ≥ 0.85 **且** 无 critical 即停（两条件同真；fidelity 缺失时按无 critical 停）；
- 防震荡：issues target 与上轮相同则停；
- 修正轮替换后重跑 _light_postprocess（不重跑补全/先验确认，C4-7/8）；
- 修正调用不可用 → 保留当前 spec 退出闭环（同 T3 语义）。

依赖注入方向：build3d_service 模块级挂载本模块（from app.services.render3d import
extreme_refine），因此本模块对 build3d_service 的引用必须延迟到函数体内，避免循环导入。
"""
from __future__ import annotations

import base64

from app.core.logging import get_logger
from app.schemas.verify import Build3dSpec

log = get_logger("render3d.extreme_refine")


def extreme_refine(spec: Build3dSpec, image_url: str) -> tuple[Build3dSpec, str]:
    """extreme 渲染对比闭环（≤3 轮）。返回 (spec, 实际模式)。

    渲染不可用 → (原 spec, "advanced")；任何 mock/异常保留当前 spec（失败不阻断）。
    签名冻结（契约 C4-5）：(spec, image_url) -> tuple[Build3dSpec, str]。
    """
    # 延迟导入：build3d_service 在模块级挂载本模块，顶层 import 会构成循环依赖
    # （_fix_call 修正；_light_postprocess 修正轮后处理；_critique_call 支持 shots 截图；
    #  _EXTREME_FIDELITY_PASS=0.85 收敛阈值唯一事实源）
    from app.services.build3d_service import (
        _EXTREME_FIDELITY_PASS, _critique_call, _fix_call, _light_postprocess,
    )
    from app.services.render3d.renderer import RenderUnavailable, render_spec

    prev_targets: set[str] | None = None
    for round_no in (1, 2, 3):
        try:
            # 契约 T4-1：render_spec 返回 3 张 PNG bytes → 转 data URL 喂 VLM（原图 + 3 截图）
            pngs = render_spec(spec.model_dump())
        except RenderUnavailable as e:
            log.warning(f"3D extreme 渲染不可用，降级 advanced: {e}")
            return spec, "advanced"
        shots = [
            f"data:image/png;base64,{base64.b64encode(b).decode('ascii')}" for b in pngs
        ]
        critique = _critique_call(spec, image_url, shots=shots)  # 原图 + 3 截图 → 含 fidelity
        if critique is None:
            break  # mock/异常：不阻断（契约 C4-3）
        critical = [i for i in critique.issues if i.severity == "critical"]
        # 收敛（契约 T4-2）：fidelity ≥ 0.85 且无 critical 两条件同真才停；
        # fidelity 缺失（LLM 未输出）时按无 critical 即停兜底
        if not critical and (critique.fidelity is None or critique.fidelity >= _EXTREME_FIDELITY_PASS):
            log.info(f"3D extreme round={round_no} 收敛早停"
                     f"（fidelity={critique.fidelity} critical=0）替换=false")
            break
        targets = {i.target for i in critique.issues}
        if targets == prev_targets:
            # 防震荡：同一批问题改不动，停止避免振荡（借 skill 的 oscillation stop）
            log.info(f"3D extreme round={round_no} 防震荡：issues target 与上轮相同，停止"
                     f"（fidelity={critique.fidelity}）")
            break
        prev_targets = targets
        new_spec = _fix_call(spec, image_url, critique.issues)
        if new_spec is None:
            break  # 修正不可用：保留当前 spec 退出（同 T3，失败不阻断）
        _light_postprocess(new_spec)  # 修正轮后处理（契约 C4-8）
        spec = new_spec
        log.info(f"3D extreme round={round_no} fidelity={critique.fidelity} "
                 f"issues={len(critique.issues)} critical={len(critical)} 替换=true")
    return spec, "extreme"
