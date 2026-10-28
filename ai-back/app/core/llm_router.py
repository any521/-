"""core.llm_router · 能力→模型路由 + 降级链执行器（05 章 / D14）

调用顺序：routing.json chain 依序尝试 → 出参 Pydantic 强校验，失败降温重试一次 →
链全部失败抛 AiRouteExhausted（携带 fallback 代号），由 service 层走规则兜底。
真实供应商走 OpenAI 兼容 /chat/completions（标准库 urllib，避免强依赖）；
mock 供应商由 app.core.mock_models 按能力生成确定性结构化结果，供联调与降级演练。
"""
from __future__ import annotations

import json
import os
import re
import urllib.error
import urllib.request
from typing import Any, Type, TypeVar

from pydantic import BaseModel, ValidationError

from app.core import mock_models
from app.core.config import get_provider, get_route
from app.core.logging import get_logger

log = get_logger("llm_router")

T = TypeVar("T", bound=BaseModel)


class AiRouteExhausted(Exception):
    """路由链全部失败：service 层据 fallback 代号执行规则兜底"""

    def __init__(self, capability: str, fallback: str):
        super().__init__(f"capability={capability} 路由链耗尽, fallback={fallback}")
        self.capability = capability
        self.fallback = fallback


class LlmResult(BaseModel):
    """路由结果：结构化出参 + 实际命中的模型（写审核日志用）"""

    model_config = {"arbitrary_types_allowed": True}

    provider: str
    model: str
    degraded: bool  # 是否非首选模型（链上第 2+ 位或 mock）
    parsed: Any  # response_model 实例


_STR_RE = re.compile(r'"(?:\\.|[^"\\])*"')
# 字符串占位符为 NUL 字符，正则用 \0（raw 字符串安全，正则引擎将其解释为 NUL）；
# 注意与模板占位符 _PLACEHOLDER_RE（{{model}} 等）区分，勿同名覆盖
_STR_PH_RE = re.compile(r"\0(\d+)\0")

# 非法特殊值 → 合法 JSON 字面量（含 Python dict 风格 True/False/None，统一所有模型输出）
_SPECIAL_VALUES = {
    "NaN": "null", "Infinity": "null", "undefined": "null",
    "None": "null", "True": "true", "False": "false",
}

# 包装对象解包键：模型可能输出 {result: {...}} / {data: [...]} 等包装
_WRAPPER_KEYS = ("result", "data", "output", "value", "json", "response", "answer", "content")


class _OutputParseError(Exception):
    """模型输出无法解析为 JSON：携带原始文本，供重试时回灌修正"""

    def __init__(self, raw_text: str, detail: str = "模型输出无法解析为 JSON"):
        super().__init__(detail)
        self.raw_text = raw_text


def _repair_json(text: str) -> dict[str, Any]:
    """LLM 输出 JSON 容错修复（零依赖，面向 DeepSeek 等开源模型常见瑕疵）：
    缺逗号/尾随逗号/单引号/注释/无引号键/NaN/True 等特殊值，以及截断补齐。
    先提取保护字符串字面量（修复不误伤字符串内容），修复后还原再解析。"""
    strings: list[str] = []

    def stash(m: re.Match[str]) -> str:
        strings.append(m.group(0))
        return f"\x00{len(strings) - 1}\x00"

    # 1) 保护双引号字符串（防后续单引号/逗号修复破坏字符串内容）
    s = _STR_RE.sub(stash, text)
    # 2) 单引号字符串/键 → 双引号（要求成对闭合，撇号不配对不误伤；
    #    转义撇号 \' 转为字面 '，避免产生 JSON 非法转义 \'）
    def _sq_to_dq(m: re.Match[str]) -> str:
        return '"' + re.sub(r"\\(?=')", "", m.group(1)) + '"'

    s = re.sub(r"'((?:\\.|[^'\\])*)'", _sq_to_dq, s)
    # 3) 保护新产生的双引号字符串（不清空，索引与第 1 步连续）
    s = _STR_RE.sub(stash, s)
    # 4) Python/JS 非法特殊值 → 合法字面量（True/False/None/NaN/Infinity/undefined）
    s = re.sub(r"\b(?:NaN|Infinity|undefined|None|True|False)\b",
               lambda m: _SPECIAL_VALUES[m.group(0)], s)
    # 5) 注释（行注释 //、块注释 /* */；字符串内已被保护）
    s = re.sub(r"//[^\n]*|/\*.*?\*/", "", s, flags=re.S)
    # 6) 尾随逗号 ,} / ,]（仅删除紧邻闭合括号的逗号，其余逗号保留）
    s = re.sub(r",(\s*[}\]])", r"\1", s)
    # 7) 缺失逗号：值边界（} ] 数字 布尔 null 字符串占位符）后紧跟值起始符（{ [ " 数字 - 占位符）
    s = re.sub(r"([}\]a-zA-Z0-9\0])\s+(?=[{\[\"\0\-0-9])", r"\1,", s)
    # 8) 无引号键 {key: → {"key":
    s = re.sub(r"([{,]\s*)([A-Za-z_][\w-]*)(\s*:)", r'\1"\2"\3', s)
    # 9) 还原字符串字面量；解析失败且疑似截断时补齐闭合括号再试
    s = _STR_PH_RE.sub(lambda m: strings[int(m.group(1))], s)
    try:
        return json.loads(s)
    except json.JSONDecodeError:
        closed = _close_truncated_json(s)
        if closed != s:
            return json.loads(closed)
        raise


def _close_truncated_json(s: str) -> str:
    """截断输出补齐：按括号配对在末尾补全缺失的 }/]（去掉尾随逗号；
    字符串内截断无法恢复，原样返回）"""
    stack: list[str] = []
    i, n = 0, len(s)
    while i < n:
        ch = s[i]
        if ch in "{[":
            stack.append(ch)
        elif ch in "}]":
            if not stack:
                return s  # 结构已损坏，放弃
            stack.pop()
        elif ch == "\x00":  # 字符串占位符：跳过直到闭合 \x00（残缺=字符串内截断，无法恢复）
            end = s.find("\x00", i + 1)
            if end < 0:
                return s
            i = end
        i += 1
    if not stack:
        return s
    head = re.sub(r",\s*$", "", s.rstrip())
    return head + "".join("}" if c == "{" else "]" for c in reversed(stack))


def _scan_blocks(text: str) -> list[str]:
    """括号配对扫描：提取所有顶层 {…} / […] 块（字符串内括号不计数），
    支持模型输出示例+结果等多块场景；嵌套只取最外层。"""
    blocks: list[str] = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch in "\"'":  # 跳过字符串字面量
            quote = ch
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == quote:
                    break
                i += 1
            i += 1
            continue
        if ch in "{[":
            depth = 0
            j = i
            while j < n:
                cj = text[j]
                if cj in "\"'":
                    quote = cj
                    j += 1
                    while j < n:
                        if text[j] == "\\":
                            j += 2
                            continue
                        if text[j] == quote:
                            break
                        j += 1
                    j += 1
                    continue
                if cj in "{[":
                    depth += 1
                elif cj in "}]":
                    depth -= 1
                    if depth == 0:
                        blocks.append(text[i : j + 1])
                        break
                j += 1
            else:
                # 未闭合（截断块）：取到末尾，交由容错修复补齐闭合括号
                blocks.append(text[i:j])
            i = j + 1 if j < n else n
            continue
        i += 1
    return blocks


def _parse_candidate(block: str) -> Any | None:
    """单个块尝试解析：标准解析 → 容错修复（含截断补齐）；失败返回 None"""
    try:
        return json.loads(block)
    except json.JSONDecodeError:
        try:
            return _repair_json(block)
        except (json.JSONDecodeError, ValueError, IndexError):
            return None


def _unwrap_wrapper(obj: Any) -> Any | None:
    """单键包装解包：{result: {...}} / {data: [...]} 等常见包装还原为目标对象"""
    if not isinstance(obj, dict) or len(obj) != 1:
        return None
    (k, v) = next(iter(obj.items()))
    if k in _WRAPPER_KEYS and isinstance(v, (dict, list)):
        return v
    return None


def _extract_candidates(text: str) -> list[Any]:
    """从模型回复中按序提取所有可解析的 JSON 候选：
    容忍任意围栏变体/前后缀/多块输出；逐个尝试标准解析→容错修复；
    包装对象 {result:…} 自动解包追加候选；去重保序。"""
    raw_blocks: list[str] = []
    for m in re.finditer(r"```[A-Za-z0-9_+.-]*\s*(.*?)```", text, re.S):
        seg = m.group(1).strip()
        if seg and seg[0] in "{[":
            raw_blocks.append(seg)
    raw_blocks.extend(b for b in _scan_blocks(text) if b not in raw_blocks)

    out: list[Any] = []
    seen: set[str] = set()
    for b in raw_blocks:
        parsed = _parse_candidate(b)
        if parsed is None:
            continue
        key = json.dumps(parsed, ensure_ascii=False, sort_keys=True)
        if key in seen:
            continue
        seen.add(key)
        out.append(parsed)
        unwrapped = _unwrap_wrapper(parsed)
        if unwrapped is not None:
            ukey = json.dumps(unwrapped, ensure_ascii=False, sort_keys=True)
            if ukey not in seen:
                seen.add(ukey)
                out.append(unwrapped)
    return out


def _extract_json(text: str) -> dict[str, Any]:
    """从模型回复中提取 JSON（兼容历史调用方）：返回第一个可解析候选"""
    candidates = _extract_candidates(text)
    if not candidates:
        raise _OutputParseError(text, "回复中未找到可解析的 JSON 对象")
    return candidates[0]


def _call_provider_text(
    provider: dict[str, Any], model: str, messages: list[dict[str, Any]], temperature: float
) -> str | dict[str, Any] | list[Any]:
    """按协议分发，返回模型原始文本（custom 模板可直接提取对象）；
    JSON 解析统一在 invoke 层做（多候选 + 回灌修正）"""
    protocol = provider.get("protocol", "openai")
    if protocol == "anthropic":
        return _call_anthropic_text(provider, model, messages, temperature)
    if protocol == "responses":
        # 火山引擎 Ark Responses API（内置模板，无需 body_template/response_text_path）；
        # 推理模型输出含 reasoning 项，默认智能提取第一个 message 的文本。
        # 注意：Ark /responses 端点强制 HTTP/2，而本服务基于 urllib（仅 HTTP/1.1），
        # 连接会被远程重置（WinError 10054）。请改用 openai 协议（/chat/completions，同样支持多模态）。
        try:
            return _call_generic_text(
                provider, model, messages, temperature,
                default_path="/responses",
                default_template=_RESPONSES_TEMPLATE,
                default_text_path="output.0.content.0.text",
                smart_text=True,
            )
        except urllib.error.URLError as e:
            raise RuntimeError(
                f"{provider['name']} 的 /responses 端点要求 HTTP/2，当前 urllib 仅支持 HTTP/1.1，"
                f"连接被远程重置（{e}）。请把该供应商 protocol 改为 openai（走 /chat/completions，Ark 同样支持多模态）。"
            ) from e
    if protocol == "custom":
        # 全参数自定义：method/request_path/body_template/response_text_path
        # 缺省回退 OpenAI 兼容模板，兼容旧配置
        return _call_generic_text(
            provider, model, messages, temperature,
            default_path="/chat/completions",
            default_template=_OPENAI_FALLBACK_TEMPLATE,
            default_text_path="choices.0.message.content",
        )
    # openai 兼容（旧逻辑保持不变）
    return _call_openai_compatible_text(provider, model, messages, temperature)


def _build_url(provider: dict[str, Any], default_path: str) -> str:
    """base_url + request_path（自定义优先，否则协议默认）

    容错：request_path 可能缺前导斜杠或带首尾空白（管理端手工录入），
    统一规整为「base_url 去尾斜杠 + / + path（去空白）」，避免拼出非法 URL。
    """
    path = (provider.get("request_path") or default_path).strip().lstrip("/")
    return provider["base_url"].rstrip("/") + "/" + path


def _http_error_detail(e: urllib.error.HTTPError) -> str:
    """读取 HTTPError 响应体，提取上游真实错误详情（OpenAI/Ark 兼容 error.code/message）"""
    try:
        raw = e.read().decode("utf-8", errors="replace")
    except OSError:
        return str(e)
    if not raw:
        return str(e)
    try:
        j = json.loads(raw)
    except json.JSONDecodeError:
        return f"{e} body={raw[:300]}"
    if isinstance(j, dict):
        err = j.get("error")
        if isinstance(err, dict):
            code, message = err.get("code"), err.get("message")
            if code or message:
                return f"{e} code={code} message={message}"
        if j.get("message"):
            return f"{e} message={j['message']}"
    return f"{e} body={raw[:300]}"


def _urlopen_with_detail(req: urllib.request.Request, timeout: float):
    """urlopen 封装：HTTPError 时读取响应体提取上游 code/message（不吞详情）"""
    try:
        return urllib.request.urlopen(req, timeout=timeout)
    except urllib.error.HTTPError as e:
        raise RuntimeError(_http_error_detail(e)) from e


# 不支持 response_format=json_object 的模型名提示词（DeepSeek R1 等推理模型会直接 400）
_JSON_MODE_BLOCKLIST = ("reasoner", "o1", "o3", "thinking")


def _supports_json_mode(model: str) -> bool:
    low = model.lower()
    return not any(h in low for h in _JSON_MODE_BLOCKLIST)


# 推理模型专项：MiMo 等默认开启思考，reasoning_content 会吃光输出预算，
# 最终 JSON 内容被截断到开头几个字符（如 "```json\n{"），导致出参解析失败/无法生成 3D spec。
# 结构化输出统一显式关闭思考（{"thinking":{"type":"disabled"}}），使完整答案直接写入 content。
# 注意：enable_thinking=false 对 MiMo 无效（实测仍输出 reasoning_content 并截断）。
_THINKING_DISABLE_MODELS = ("mimo",)


def _disable_thinking(model: str) -> bool:
    low = model.lower()
    return any(k in low for k in _THINKING_DISABLE_MODELS)


def _call_openai_compatible_text(
    provider: dict[str, Any], model: str, messages: list[dict[str, Any]], temperature: float
) -> str:
    """OpenAI 兼容 /chat/completions 调用（返回模型原始文本）"""
    api_key = provider.get("api_key") or ""
    if not api_key:
        raise RuntimeError(f"供应商 {provider['name']} 未配置密钥")
    body: dict[str, Any] = {
        "model": model, "messages": messages, "temperature": temperature,
        "max_tokens": int(provider.get("max_tokens") or 4096),
    }
    if _supports_json_mode(model):
        body["response_format"] = {"type": "json_object"}
    if _disable_thinking(model):
        # 关闭 MiMo 等推理模型的思考：否则长 JSON（3D spec）被 reasoning_content 截断，
        # content 只剩开头几个字符无法解析
        body["thinking"] = {"type": "disabled"}
    req = urllib.request.Request(
        _build_url(provider, "/chat/completions"),
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
            **(provider.get("extra_headers") or {}),
        },
        method="POST",
    )
    with _urlopen_with_detail(req, provider.get("timeout_seconds", 8)) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return data["choices"][0]["message"]["content"]


def _call_anthropic_text(
    provider: dict[str, Any], model: str, messages: list[dict[str, Any]], temperature: float
) -> str:
    """Anthropic /v1/messages 调用（system 作为顶层字段，响应 content[0].text）"""
    api_key = provider.get("api_key") or ""
    if not api_key:
        raise RuntimeError(f"供应商 {provider['name']} 未配置密钥")
    system_text = "\n".join(
        m["content"] for m in messages if m["role"] == "system" and isinstance(m["content"], str))
    user_messages = [m for m in messages if m["role"] != "system"]
    body = json.dumps({
        "model": model, "max_tokens": int(provider.get("max_tokens") or 4096),
        "temperature": temperature,
        "system": system_text or None, "messages": user_messages,
    }).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01",
    }
    headers.update(provider.get("extra_headers") or {})
    req = urllib.request.Request(
        _build_url(provider, "/v1/messages"),
        data=body, headers=headers, method="POST",
    )
    with _urlopen_with_detail(req, provider.get("timeout_seconds", 8)) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return data["content"][0]["text"]


# ---------- 通用模板调用（responses / custom 协议） ----------

# 火山引擎 Ark Responses API 内置模板（多模态 input_image/input_text 自动转换）
# 注意：Ark 对未知字段严格校验，输出上限参数是 max_output_tokens（不是 max_tokens），
# 传 max_tokens 会直接 400 InvalidParameter: unknown field
_RESPONSES_TEMPLATE = (
    '{"model": {{model}}, "input": {{input}}, "temperature": {{temperature}},'
    ' "max_output_tokens": {{max_tokens}}}'
)

# custom 协议未配置 body_template 时的 OpenAI 兼容回退模板（与旧行为一致）
_OPENAI_FALLBACK_TEMPLATE = (
    '{"model": {{model}}, "messages": {{messages}}, "temperature": {{temperature}},'
    ' "max_tokens": {{max_tokens}}, "response_format": {"type": "json_object"}}'
)

# 模板占位符：标量按 JSON 编码注入（字符串带引号、数字裸值），数组/对象注入裸 JSON
_PLACEHOLDER_RE = re.compile(r"\{\{(model|temperature|max_tokens|text|image|messages|input)\}\}")


def _messages_to_input(messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """OpenAI 内部消息格式 → Responses API input 格式

    - content 为字符串 → [{"type": "input_text", "text": ...}]
    - content 为多模态数组 → text→input_text、image_url→input_image（url 提取字符串）
    - 未知类型片段原样保留
    """
    out: list[dict[str, Any]] = []
    for m in messages:
        content = m.get("content")
        items: list[dict[str, Any]] = []
        if isinstance(content, str):
            items.append({"type": "input_text", "text": content})
        elif isinstance(content, list):
            for part in content:
                ptype = part.get("type")
                if ptype == "text":
                    items.append({"type": "input_text", "text": part.get("text", "")})
                elif ptype == "image_url":
                    url = part.get("image_url")
                    if not isinstance(url, str):
                        url = (url or {}).get("url", "") if isinstance(url, dict) else ""
                    items.append({"type": "input_image", "image_url": url})
                else:
                    items.append(part)
        else:
            continue
        out.append({"role": m.get("role", "user"), "content": items})
    return out


def _messages_to_text(messages: list[dict[str, Any]]) -> str:
    """全部消息拼接为纯文本（多模态片段只取 text 部分）"""
    parts: list[str] = []
    for m in messages:
        content = m.get("content")
        if isinstance(content, str):
            parts.append(content)
        elif isinstance(content, list):
            parts.extend(
                part.get("text", "") for part in content if part.get("type") == "text"
            )
    return "\n".join(parts)


def _first_image(messages: list[dict[str, Any]]) -> str:
    """第一条多模态消息中的图片 URL（无图返回空串）"""
    for m in messages:
        content = m.get("content")
        if not isinstance(content, list):
            continue
        for part in content:
            if part.get("type") != "image_url":
                continue
            url = part.get("image_url")
            if isinstance(url, str):
                return url
            if isinstance(url, dict) and url.get("url"):
                return url["url"]
    return ""


def _render_template(
    template: str, model: str, messages: list[dict[str, Any]],
    temperature: float, max_tokens: int,
) -> str:
    """替换模板占位符：标量 JSON 编码（字符串带引号），数组/对象裸 JSON 注入"""
    values = {
        "model": json.dumps(model, ensure_ascii=False),
        "temperature": json.dumps(temperature),
        "max_tokens": json.dumps(max_tokens),
        "text": json.dumps(_messages_to_text(messages), ensure_ascii=False),
        "image": json.dumps(_first_image(messages), ensure_ascii=False),
        "messages": json.dumps(messages, ensure_ascii=False),
        "input": json.dumps(_messages_to_input(messages), ensure_ascii=False),
    }
    return _PLACEHOLDER_RE.sub(lambda m: values[m.group(1)], template)


def _extract_path(data: dict[str, Any], path: str) -> Any:
    """点路径取值：dict 按 key、list 按下标（如 output.0.content.0.text）"""
    node: Any = data
    for seg in path.split("."):
        try:
            if isinstance(node, list):
                node = node[int(seg)]
            else:
                node = node[seg]
        except (KeyError, IndexError, TypeError, ValueError) as e:
            # 响应结构与配置的提取路径不匹配：带上实际响应摘要便于定位
            summary = json.dumps(data, ensure_ascii=False)[:400]
            raise RuntimeError(
                f"响应提取失败 path={path} seg={seg} ({e})，实际响应：{summary}"
            ) from e
    return node


def _raise_if_upstream_error(data: dict[str, Any]) -> None:
    """OpenAI Responses 兼容的业务错误也走 HTTP 200：output[0].type=error / 顶层 error"""
    out = data.get("output")
    if isinstance(out, list) and out and isinstance(out[0], dict) and out[0].get("type") == "error":
        err = out[0]
        raise RuntimeError(
            f"上游返回业务错误: code={err.get('code')} message={err.get('message')}"
        )
    err = data.get("error")
    if isinstance(err, dict):
        raise RuntimeError(
            f"上游返回错误: code={err.get('code')} message={err.get('message')}"
        )


def _extract_responses_text(data: dict[str, Any]) -> str:
    """Responses 输出智能提取：推理模型会先输出 reasoning（思维链，无 content），
    跳过非 message 项，取第一个 message 的 output_text/text 文本"""
    for item in data.get("output") or []:
        if not isinstance(item, dict) or item.get("type") not in ("message", "output"):
            continue
        content = item.get("content")
        if isinstance(content, list):
            for part in content:
                if (
                    isinstance(part, dict)
                    and part.get("type") in ("output_text", "text")
                    and part.get("text")
                ):
                    return part["text"]
        elif isinstance(content, dict) and content.get("text"):
            return content["text"]
    summary = json.dumps(data, ensure_ascii=False)[:400]
    raise RuntimeError(f"响应中未找到 message 文本输出，实际响应：{summary}")


def _call_generic_text(
    provider: dict[str, Any], model: str, messages: list[dict[str, Any]], temperature: float,
    *, default_path: str, default_template: str, default_text_path: str,
    smart_text: bool = False,
) -> str | dict[str, Any] | list[Any]:
    """通用模板调用：method + url + headers + body 模板 + 响应文本点路径全部可自定义；
    返回模型原始文本（模板直接提取到对象时原样返回）"""
    api_key = provider.get("api_key") or ""
    if not api_key:
        raise RuntimeError(f"供应商 {provider['name']} 未配置密钥")
    template = provider.get("body_template") or default_template
    body = _render_template(
        template, model, messages, temperature,
        int(provider.get("max_tokens") or 4096),
    ).encode("utf-8")
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {api_key}"}
    headers.update(provider.get("extra_headers") or {})
    method = (provider.get("method") or "POST").upper()
    text_path = provider.get("response_text_path")
    req = urllib.request.Request(
        _build_url(provider, default_path),
        data=None if method == "GET" else body,
        headers=headers, method=method,
    )
    with _urlopen_with_detail(req, provider.get("timeout_seconds", 8)) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    _raise_if_upstream_error(data)
    # 显式配置的提取路径优先；responses 协议默认智能提取（跳过 reasoning）；
    # 其余协议回退协议默认路径
    if text_path:
        text = _extract_path(data, text_path)
    elif smart_text:
        text = _extract_responses_text(data)
    else:
        text = _extract_path(data, default_text_path)
    return text


# 统一结构化输出铁律：invoke 时自动注入（对所有模型生效；幂等，含标记不重复注入）
_FORMAT_RULE_MARK = "【输出格式铁律】"
_FORMAT_RULE = (
    _FORMAT_RULE_MARK +
    "你只能输出一个合法的 JSON 对象：禁止 Markdown 代码围栏（```）、注释、解释文字"
    "与任何前后缀；键名严格按给定 schema；无法确定的字段输出 null 或空数组；"
    "不要输出示例，只输出最终结果本身。"
)


def _inject_format_rule(messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """幂等注入统一输出格式铁律：追加到已有 system 消息，无 system 则插入首条"""
    msgs = [dict(m) for m in messages]
    for m in msgs:
        if m.get("role") == "system" and isinstance(m.get("content"), str):
            if _FORMAT_RULE_MARK not in m["content"]:
                m["content"] = m["content"] + "\n" + _FORMAT_RULE
            return msgs
    return [{"role": "system", "content": _FORMAT_RULE}] + msgs


def _with_correction(
    messages: list[dict[str, Any]], raw_text: str, err: Exception, max_chars: int = 3000
) -> list[dict[str, Any]]:
    """把模型上次的原始输出与错误信息回灌，要求重新输出修正后的 JSON（自我修正）"""
    msgs = [dict(m) for m in messages]
    snippet = raw_text if len(raw_text) <= max_chars else raw_text[:max_chars] + "\n…(已截断)"
    msgs.append({"role": "assistant", "content": snippet})
    msgs.append({"role": "user", "content": (
        "你上次的输出不符合要求，无法通过解析/校验。请修正后只输出一个完整、合法的 JSON 对象，"
        "不要任何解释、代码围栏或前后缀文本。\n"
        f"错误信息：{str(err)[:500]}"
    )})
    return msgs


def _validate_candidates(
    candidates: list[Any], response_model: Type[T]
) -> tuple[T | None, Exception | None]:
    """候选逐个过 Pydantic 校验，返回（首个通过, 最后一个错误）"""
    last_err: Exception | None = None
    for cand in candidates:
        try:
            return response_model.model_validate(cand), None
        except ValidationError as e:
            last_err = e
    return None, last_err


def invoke(
    capability: str,
    messages: list[dict[str, Any]],
    response_model: Type[T],
    context: dict[str, Any] | None = None,
    temperature: float = 0.2,
    timeout_seconds: float | None = None,
) -> LlmResult:
    """按能力路由调用；context 供 mock 供应商生成确定性结果；
    timeout_seconds 非空时覆盖供应商默认读超时（如 3D spec 长输出 > 识别默认 15s）。
    统一处理链：注入格式铁律 → 多候选提取/解析 → Pydantic 强校验；
    失败时把原始输出与错误信息回灌重试（降温），链耗尽抛 AiRouteExhausted。"""
    route = get_route(capability)
    max_retries = int(route.get("max_retries", 0))
    decay = float(route.get("retry_temperature_decay", 0.5))
    messages = _inject_format_rule(messages)

    for idx, hop in enumerate(route["chain"]):
        provider = get_provider(hop["provider"])
        if provider is None:
            continue
        if timeout_seconds is not None:
            provider = {**provider, "timeout_seconds": timeout_seconds}
        model = hop["model"]
        temp = temperature
        attempt_messages = messages
        for attempt in range(max_retries + 1):
            try:
                if provider["vendor"] == "internal":
                    raw: Any = mock_models.generate(capability, context or {})
                else:
                    raw = _call_provider_text(provider, model, attempt_messages, temp)
                if isinstance(raw, (dict, list)):
                    candidates: list[Any] = [raw]
                else:
                    candidates = _extract_candidates(raw)
                    if not candidates:
                        raise _OutputParseError(raw)
                parsed, verr = _validate_candidates(candidates, response_model)
                if parsed is not None:
                    return LlmResult(provider=provider["name"], model=model,
                                     degraded=idx > 0 or provider["vendor"] == "internal",
                                     parsed=parsed)
                raise verr if verr else _OutputParseError(str(raw))
            except (_OutputParseError, ValidationError, ValueError) as e:
                # 结构化输出解析/校验失败（错误码 3002 场景）：回灌原始输出 + 错误信息重试
                log.warning(f"{capability} 出参校验失败 provider={provider['name']} "
                            f"attempt={attempt}: {e}")
                raw_text = raw if isinstance(raw, str) else json.dumps(raw, ensure_ascii=False)
                attempt_messages = _with_correction(attempt_messages, raw_text, e)
                temp *= decay
            except (urllib.error.URLError, TimeoutError, RuntimeError, OSError) as e:
                log.warning(f"{capability} 供应商不可用 provider={provider['name']}: {e}")
                break  # 网络类错误不重试本供应商，直接切下一跳
    raise AiRouteExhausted(capability, route.get("fallback", "MANUAL"))
