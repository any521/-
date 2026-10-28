"""core.security · 内网服务间 Token 鉴权（05 章：AI 服务仅接受内网调用）"""
from __future__ import annotations

import hmac

from fastapi import Header, HTTPException

from app.core.config import INTERNAL_TOKEN


async def require_internal_token(
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> None:
    """FastAPI 依赖：校验内网 Token

    生产环境（INTERNAL_TOKEN 已配置）必须匹配，使用 hmac.compare_digest 恒定时间比较
    防止时序攻击。开发模式（INTERNAL_TOKEN 为空）放行。
    """
    if not INTERNAL_TOKEN:
        return  # 开发模式：未配置 Token，放行
    if x_internal_token is None or not hmac.compare_digest(x_internal_token, INTERNAL_TOKEN):
        raise HTTPException(status_code=401, detail="invalid internal token")
