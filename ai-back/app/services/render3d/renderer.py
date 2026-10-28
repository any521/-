# ============================================================
# render3d/renderer.py —— Playwright 无头渲染桥接（Track G · T4 extreme 闭环的渲染端）
# 进程级单例 headless Chromium + threading.Semaphore(1) 限并发=1（渲染是重资源操作，排队不并行）。
# 渲染页（render_page/index.html + spec-executor.iife.js）只消费 JSON spec（数据驱动），
# 不执行任何 LLM 生成的代码，无代码注入面。
# 本模块惰性 import playwright：未安装/启动失败/渲染失败 → 抛 RenderUnavailable，
# 由 extreme 闭环捕获后降级 advanced（契约 C4-3，顶层不依赖 playwright）。
# ============================================================
import base64
import json
import threading
from pathlib import Path

try:
    from playwright.sync_api import sync_playwright
except ImportError:  # 未安装 playwright：模块仍可导入，调用 render_spec 时抛 RenderUnavailable
    sync_playwright = None

_RENDER_PAGE_URL = (Path(__file__).resolve().parent / "render_page" / "index.html").as_uri()
_VIEW_SIZE = 512  # 与 renderEntry.ts 的 VIEW_SIZE 对齐
_SHOTS = 3  # front / three-quarter / top 三视角


class RenderUnavailable(RuntimeError):
    """渲染设施不可用（未装 playwright / Chromium 缺失 / 启动或渲染失败）。"""


_semaphore = threading.Semaphore(1)  # 渲染排队不并行
_browser = None
_browser_lock = threading.Lock()


def _get_browser():
    """进程级单例浏览器（惰性启动，失败抛 RenderUnavailable）。"""
    global _browser
    if sync_playwright is None:
        raise RenderUnavailable("playwright 未安装（python -m playwright install chromium）")
    if _browser is not None and _browser.is_connected():
        return _browser
    with _browser_lock:
        if _browser is not None and _browser.is_connected():
            return _browser
        try:
            _pw = sync_playwright().start()
            _browser = _pw.chromium.launch(
                headless=True,
                args=[
                    "--use-gl=angle", "--use-angle=swiftshader",  # 显式软件 WebGL（无 GPU 服务器截图必需）
                    "--enable-unsafe-swiftshader",                 # Chromium 128+ 需显式放行软渲染
                    # Dockerfile 已使用非 root 用户（app），无需 --no-sandbox
                    "--disable-dev-shm-usage",                     # /dev/shm 不足时防崩溃
                ],
            )
        except Exception as e:
            raise RenderUnavailable(f"Chromium 启动失败: {e}") from e
        return _browser


def render_spec(spec: dict) -> list[bytes]:
    """渲染 spec → 3 张 PNG（front / three-quarter / top，512×512）。

    任何失败（import/启动/页面/渲染）抛 RenderUnavailable，由上层降级处理。
    并发安全：Semaphore(1) 排队，同一时刻至多一个渲染任务。
    """
    with _semaphore:
        try:
            browser = _get_browser()
            page = browser.new_page(viewport={"width": _VIEW_SIZE, "height": _VIEW_SIZE})
            try:
                page.goto(_RENDER_PAGE_URL)  # 本地 file:// 页面，零外网依赖
                page.wait_for_function("window.renderSpec !== undefined", timeout=15000)
                data_urls = page.evaluate(
                    "(specJson) => window.renderSpec(specJson)",
                    json.dumps(spec, ensure_ascii=False),
                )
            finally:
                page.close()
            if not isinstance(data_urls, list) or len(data_urls) != _SHOTS:
                raise RenderUnavailable(
                    f"渲染器返回异常（{type(data_urls).__name__}，"
                    f"{len(data_urls) if isinstance(data_urls, list) else '?'} 张）")
            return [base64.b64decode(u.split(",", 1)[1]) for u in data_urls]
        except RenderUnavailable:
            raise
        except Exception as e:
            raise RenderUnavailable(f"渲染失败: {e}") from e
