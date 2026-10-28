"""schemas.common · 统一响应壳（对齐 Java 侧 Result：HTTP 恒 200，code 承载语义 / D4）"""
from __future__ import annotations

from typing import Any, Generic, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")

# 与 dianfei-common ResultCode 对齐的错误码子集
CODE_SUCCESS = 200
CODE_PARAM_ERROR = 400
CODE_UNAUTHORIZED = 401
CODE_RATE_LIMIT = 429
CODE_AI_UNAVAILABLE = 3001
CODE_AI_SCHEMA_INVALID = 3002
CODE_SYSTEM_ERROR = 5000


class Result(BaseModel, Generic[T]):
    code: int = CODE_SUCCESS
    msg: str = "success"
    data: T | None = None
    trace_id: str = Field(default="-", description="请求追踪 ID，贯穿 Java/Python 两侧日志")

    @classmethod
    def ok(cls, data: Any = None, trace_id: str = "-") -> "Result[Any]":
        return cls(code=CODE_SUCCESS, msg="success", data=data, trace_id=trace_id)

    @classmethod
    def fail(cls, code: int, msg: str, trace_id: str = "-") -> "Result[Any]":
        return cls(code=code, msg=msg, data=None, trace_id=trace_id)
