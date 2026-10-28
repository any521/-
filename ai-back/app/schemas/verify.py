"""schemas.verify · F1 视觉审核出入参（Sprint 0 冻结，业务线据此 Mock）

管线分工：pHash 防重 + EXIF 校验在 Java 侧完成；本服务只做「视觉识别 + 阈值决策建议」。
最终决策（自动通过/冻结待人审/驳回）由 Java 编排层结合 sys_config 阈值落库。
"""
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator, model_validator


class VerifyImageRequest(BaseModel):
    biz_type: Literal["RECYCLE", "KITCHEN"] = Field(description="业务来源：回收完成照 / 厨余投放照")
    biz_id: int | None = Field(default=None, description="来源单 ID（回收单/投放上报）；下单前品类识别场景为空")
    image_url: str = Field(description="MinIO 图片地址（内网可达）")
    claimed_category: str | None = Field(default=None, description="用户申报品类，用于一致性比对")
    claimed_bags: int | None = Field(default=None, description="厨余申报袋数")


class VisionOutput(BaseModel):
    """视觉大模型结构化出参（Pydantic 强校验，失败降温重试）"""

    category: str = Field(description="识别品类")
    purity: float = Field(ge=0, le=1, description="纯净度 0-1")
    confidence: float = Field(ge=0, le=1, description="识别置信度 0-1")
    violations: list[str] = Field(default_factory=list, description="违规项，空=无违规")
    description: str = Field(default="", description="识别说明")


class VerifyImageResponse(BaseModel):
    suggestion: Literal["AUTO_PASS", "MANUAL_REVIEW", "REJECT"] = Field(
        description="阈值决策建议：confidence≥pass 且无违规→AUTO_PASS；有违规→REJECT；其余→MANUAL_REVIEW")
    category: str
    category_match: bool = Field(description="识别品类与申报品类是否一致")
    purity: float
    confidence: float
    violations: list[str]
    description: str
    model_name: str = Field(description="实际命中模型，写 biz_ai_verify_log.model_name")
    degraded: bool = Field(description="是否走了降级链（非首选模型/mock）")
    elapsed_ms: int


# ==================== 3D 构建（img2threejs 风格：代码即模型，spec 由视觉 LLM 从真实图片提取） ====================

class DecalItem(BaseModel):
    """面/侧面纹理上的一个绘制元素：品牌标识/产品名/图案色块/条码区等
    （region 归一化 0-1，画布原点左上；按声明顺序绘制，后画的在上层）"""

    type: Literal["text", "circle", "rect", "stripe"]
    text: str = Field(default="", description="文字内容（OCR 近似：品牌名/产品名等短文字）")
    color: str = Field(default="#333333", description="文字/图形颜色（取真实像素）")
    region: list[float] = Field(default_factory=lambda: [0.0, 0.0, 1.0, 0.2], description="归一化区域 [x,y,宽,高]（0-1）")
    fontSize: float = Field(default=0.6, ge=0.05, le=1, description="文字高度占 region 高度的比例")
    border: bool = Field(default=False, description="rect 为描边模式（条码框/标签框）")

    @field_validator("region", mode="before")
    @classmethod
    def _norm_region(cls, v):
        """LLM 可能输出对象 {x,y,w,h} 而非数组 [x,y,w,h]（补全阶段实测）→ 归一化"""
        if isinstance(v, dict):
            return [v.get("x", 0.0), v.get("y", 0.0), v.get("w", 1.0), v.get("h", 0.2)]
        return v

    @field_validator("fontSize", mode="before")
    @classmethod
    def _norm_font_size(cls, v):
        """LLM 常把 0-1 比例写成 0-100 的整数（补全阶段实测 fontSize=10/12）→ >1 时除以 100 并夹回 [0.05,1]"""
        if isinstance(v, (int, float)) and v > 1:
            return min(1.0, max(0.05, v / 100.0))
        return v

    @field_validator("border", mode="before")
    @classmethod
    def _norm_border(cls, v):
        """LLM 对非边框元素输出 null（bool 不接受 None）→ 归一为 False"""
        return False if v is None else v


class FaceMaterial(BaseModel):
    """box 单面贴图材质（materials 数组按 +X,-X,+Y,-Y,+Z,-Z 对应六面）"""

    bg: str = Field(default="#ffffff", description="底色 #rrggbb 或 transparent")
    # 上限 10：正面主视觉典型 7+ 元素（logo/色带/产品名/插画/徽章/净含量/条码区），6 会逼 LLM 省略
    items: list[DecalItem] = Field(default_factory=list, max_length=10)


class SideLabel(BaseModel):
    """侧面环绕标签（cylinder/cone/lathe/tube 侧面纹理：画布横向环绕展开）"""

    bg: str = Field(default="transparent", description="底色 #rrggbb 或 transparent")
    items: list[DecalItem] = Field(default_factory=list, max_length=10)


class SpecPrimitive(BaseModel):
    """单个几何体（前端执行器 three/specModel.ts 渲染）"""

    type: Literal["box", "cylinder", "sphere", "cone", "plane"]
    size: list[float] | None = Field(default=None, description="box/plane 尺寸 [宽,高,深]")
    radius: float | None = Field(default=None, description="cylinder/sphere/cone 半径")
    height: float | None = Field(default=None, description="cylinder/cone 高度")
    pos: list[float] = Field(default_factory=lambda: [0.0, 0.0, 0.0], description="位置（底部中心原点，整体高=1）")
    rot: list[float] = Field(default_factory=lambda: [0.0, 0.0, 0.0], description="旋转（弧度）")
    color: str = Field(default="#c8c8c8", description="颜色 #rrggbb，取自真实图片")
    roughness: float = Field(default=0.6, ge=0, le=1, description="粗糙度")
    metalness: float = Field(default=0.0, ge=0, le=1, description="金属度")
    transparent: bool = Field(default=False)
    opacity: float = Field(default=1.0, ge=0, le=1)
    surface: Literal["none", "paper", "plastic", "metal", "glass"] = Field(default="none", description="材质模拟：纸/塑料/金属/玻璃（PBR 参数 + 纤维/微噪凹凸贴图）")
    # 元素允许 None：LLM 留空面会输出 null（=该面复用主体材质），与前端 specModel.ts buildBoxMaterials 宽容语义一致；
    # 不接受 null 会导致整个 spec 校验失败（历史坑：9 元素超限之外的另一失败点）
    materials: list[FaceMaterial | None] | None = Field(default=None, max_length=6, description="box 六面贴图材质（贴图优先：品牌/文字/图案逐面还原）")
    label: SideLabel | None = Field(default=None, description="侧面环绕标签（cylinder/cone 等旋转体）")


def _decimate_points(points: list[list[float]], limit: int) -> list[list[float]]:
    """轮廓点超上限抽稀（保形）：均匀取点保留首尾与整体形状。
    （实测 MiMo 等模型会输出 20+ 点的高密度轮廓，直接超 max_length 导致整份 spec 校验失败。）"""
    if len(points) <= limit:
        return points
    step = (len(points) - 1) / (limit - 1)
    return [points[round(i * step)] for i in range(limit)]


class LatheSpec(BaseModel):
    """车削体（LatheGeometry）：轮廓点 [半径, 高度] 绕 Y 轴旋转 → 瓶身/杯身等旋转对称曲面
    （腰线、肩部弧线等弯曲程度由轮廓点精确表达）"""

    points: list[list[float]] = Field(min_length=2, max_length=24, description="轮廓点 [[r,y],...]（底部到顶部，r 为半径 y 为高度）")

    @field_validator("points", mode="before")
    @classmethod
    def _decimate_points_field(cls, v):
        """模型输出超 24 点时均匀抽稀到 24 点（保形，不整份拒绝）；
        渲染端 LatheGeometry 接受任意点数，抽稀仅用于满足出参强校验。"""
        if isinstance(v, list):
            return _decimate_points(v, 24)
        return v
    pos: list[float] = Field(default_factory=lambda: [0.0, 0.0, 0.0])
    rot: list[float] = Field(default_factory=lambda: [0.0, 0.0, 0.0])
    color: str = Field(default="#c8c8c8")
    roughness: float = Field(default=0.6, ge=0, le=1)
    metalness: float = Field(default=0.0, ge=0, le=1)
    transparent: bool = Field(default=False)
    opacity: float = Field(default=1.0, ge=0, le=1)
    surface: Literal["none", "paper", "plastic", "metal", "glass"] = Field(default="none")
    label: SideLabel | None = Field(default=None, description="侧面环绕标签")


class TubeSpec(BaseModel):
    """管道（TubeGeometry）：路径点 + 半径 → 吸管等弯曲部件（弯曲程度由 curve 点列表达）"""

    curve: list[list[float]] = Field(min_length=2, max_length=24, description="路径点 [[x,y,z],...]")
    radius: float = Field(default=0.05, gt=0, description="管道半径")
    pos: list[float] = Field(default_factory=lambda: [0.0, 0.0, 0.0])
    rot: list[float] = Field(default_factory=lambda: [0.0, 0.0, 0.0])
    color: str = Field(default="#c8c8c8")
    roughness: float = Field(default=0.6, ge=0, le=1)
    metalness: float = Field(default=0.0, ge=0, le=1)
    transparent: bool = Field(default=False)
    opacity: float = Field(default=1.0, ge=0, le=1)
    surface: Literal["none", "paper", "plastic", "metal", "glass"] = Field(default="none")

    @field_validator("curve", mode="before")
    @classmethod
    def _decimate_curve_field(cls, v):
        if isinstance(v, list):
            return _decimate_points(v, 24)
        return v


class DecalSpec(BaseModel):
    """表面贴花（CanvasTexture）：文字/logo/图标近似——内容与颜色从真实图片提取，
    前端 canvas 绘制贴到对应面（text 文字 / circle 圆形标 / rect 边框 / stripe 色条）"""

    text: str = Field(default="", description="文字内容（OCR 近似，勿臆造大段文字）")
    shape: Literal["text", "circle", "rect", "stripe"] = Field(default="text")
    color: str = Field(default="#333333", description="文字/图形颜色（取真实像素）")
    bg: str = Field(default="transparent", description="底色 #rrggbb 或 transparent")
    pos: list[float] = Field(default_factory=lambda: [0.0, 0.5, 0.01], description="贴花中心位置（贴在表面外侧）")
    rot: list[float] = Field(default_factory=lambda: [0.0, 0.0, 0.0])
    size: list[float] = Field(default_factory=lambda: [0.4, 0.15], description="贴花宽高（模型比例）")
    fontSize: float = Field(default=0.06, gt=0, description="文字高度（模型比例）")


_ITEM_NAME_KEYS = ("name", "item", "title", "物品名")


def _coerce_item_name(value: Any) -> str:
    """LLM 偶发把 item 输出成结构化对象/缺失，归一化为纯字符串物品名。
    优先完整名键（name/item/title 自带品牌），其次品牌+品名拼接，兜底通用占位。
    禁止抓取非名称字段（如营养值/净含量），避免把「3.2g/100mL」当物品名。"""
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, dict):
        brand = value.get("brand")
        brand = brand.strip() if isinstance(brand, str) else ""
        for k in _ITEM_NAME_KEYS:
            v = value.get(k)
            if isinstance(v, str) and v.strip():
                return v.strip()
        for k in ("product_name", "product"):
            v = value.get(k)
            if isinstance(v, str) and v.strip():
                return brand + v.strip() if brand else v.strip()
        return brand or "物品"
    if isinstance(value, (list, tuple)):
        parts = [x.strip() for x in value if isinstance(x, str) and x.strip()]
        return "".join(parts) or "物品"
    return "物品"


def _primitive_to_lathe_points(entry: dict) -> list[list[float]] | None:
    """primitive 风格对象 → lathe 轮廓点换算（mi-mo 等实测把 cylinder 等几何体混入 lathes）。

    cylinder/cone/sphere 尺寸来源 radius/height（优先）或 size [宽,高,深]；
    换算失败（缺尺寸/非正数）返回 None，由调用方丢弃该条目（3D 构建锦上添花，
    保住整份 spec 可渲染部分优先，绝不因单个坏条目拒绝整包）。"""
    typ = entry.get("type")
    radius = entry.get("radius")
    height = entry.get("height")
    size = entry.get("size")
    if not isinstance(radius, (int, float)) and isinstance(size, list) and len(size) >= 2:
        radius = size[0] / 2.0
    if not isinstance(height, (int, float)) and isinstance(size, list) and len(size) >= 2:
        height = size[1]
    if not isinstance(radius, (int, float)) or not isinstance(height, (int, float)):
        return None
    if radius <= 0 or height <= 0:
        return None
    if typ == "cylinder":
        return [[radius, 0.0], [radius, height]]
    if typ == "cone":
        return [[0.0, 0.0], [radius, height]]
    if typ == "sphere":
        return [[0.0, 0.0], [radius, radius], [0.0, 2 * radius]]
    return None


class Build3dSpec(BaseModel):
    """3D 构建 spec：视觉 LLM 看真实图片后输出的几何体组合（img2threejs 分阶段雕刻的运行时版）"""

    item: str = Field(description="物品名（如 牛奶盒）")
    primitives: list[SpecPrimitive] = Field(default_factory=list, max_length=40, description="几何体组合 5-12 个")
    lathes: list[LatheSpec] = Field(default_factory=list, max_length=4, description="车削体（瓶身/杯身曲线）")
    tubes: list[TubeSpec] = Field(default_factory=list, max_length=4, description="管道（弯曲吸管/把手）")
    decals: list[DecalSpec] = Field(default_factory=list, max_length=10, description="表面贴花（文字/logo/图标）")
    aspect: list[float] = Field(default_factory=lambda: [1.0, 1.0, 1.0], description="整体包围盒比例 [宽,高,深]（高=1，对照实物照片校准）")
    tick: Literal["none", "float", "sway", "spin"] = Field(default="float", description="待机动画类型")

    @model_validator(mode="before")
    @classmethod
    def _coerce_item_to_string(cls, data: Any) -> Any:
        """防御：item 归一为字符串 + lathes/primitives/tubes 数组容错修复。

        实测 mi-mo 等模型把 primitive 风格对象（type=cylinder 等，无 points）混入 lathes，
        LatheSpec.points 必填 → 整份 spec 校验失败（出参强校验拒绝）→ 3D 构建回退预设模型库。
        修复策略（3D 是锦上添花，保可渲染部分优先）：
        - lathes 内 cylinder/cone/sphere 风格条目 → 按几何语义换算 points 轮廓；
        - lathes 内 box/plane → 挪入 primitives（本就是几何体）；
        - primitives 内 lathe 风格条目（无 type 含 points）→ 挪入 lathes；
        - tubes 缺 curve 但 cylinder/cone 语义 → 换算直管曲线；
        - 无法换算的坏条目一律丢弃，不拒绝整包。"""
        if not isinstance(data, dict):
            return data
        d = dict(data)
        if not isinstance(d.get("item"), str):
            d["item"] = _coerce_item_name(d.get("item") or d)

        # ---- lathes：primitive 风格条目 → 换算 points / 挪入 primitives / 丢弃 ----
        lathes = d.get("lathes")
        if isinstance(lathes, list):
            repaired: list[dict[str, Any]] = []
            moved_to_prims: list[dict[str, Any]] = []
            for entry in lathes:
                if not isinstance(entry, dict):
                    continue
                if isinstance(entry.get("points"), list):
                    repaired.append(entry)
                    continue
                if entry.get("type") in ("box", "plane") and isinstance(entry.get("size"), list):
                    moved_to_prims.append(entry)
                    continue
                pts = _primitive_to_lathe_points(entry)
                if pts is not None:
                    repaired.append({**entry, "points": pts})
            d["lathes"] = repaired
            if moved_to_prims:
                prims = list(d.get("primitives") or [])
                room = max(0, 40 - len(prims))
                d["primitives"] = prims + moved_to_prims[:room]

        # ---- primitives：lathe 风格条目（无 type 含 points）→ 挪入 lathes；其余坏条目丢弃 ----
        prims = d.get("primitives")
        if isinstance(prims, list):
            kept: list[dict[str, Any]] = []
            moved_to_lathes: list[dict[str, Any]] = []
            for entry in prims:
                if not isinstance(entry, dict):
                    continue
                if "type" not in entry and isinstance(entry.get("points"), list):
                    moved_to_lathes.append(entry)
                elif isinstance(entry.get("type"), str):
                    kept.append(entry)
            d["primitives"] = kept
            if moved_to_lathes:
                lathes = list(d.get("lathes") or [])
                room = max(0, 4 - len(lathes))
                d["lathes"] = lathes + moved_to_lathes[:room]

        # ---- tubes：缺 curve → cylinder/cone 语义换算直管；否则丢弃 ----
        tubes = d.get("tubes")
        if isinstance(tubes, list):
            kept_tubes: list[dict[str, Any]] = []
            for entry in tubes:
                if not isinstance(entry, dict):
                    continue
                if isinstance(entry.get("curve"), list):
                    kept_tubes.append(entry)
                    continue
                typ = entry.get("type")
                radius = entry.get("radius")
                height = entry.get("height")
                if (typ in ("cylinder", "cone") and isinstance(radius, (int, float))
                        and isinstance(height, (int, float)) and radius > 0 and height > 0):
                    kept_tubes.append({**entry, "curve": [[0.0, 0.0, 0.0], [0.0, height, 0.0]]})
            d["tubes"] = kept_tubes
        return d


# ==================== 3D 质量分级（Track G）：standard 默认 / advanced 多轮自纠 / extreme 渲染闭环 ====================

# 构建模式：standard=单次构建（现状链路）；advanced=+spec 级多轮自纠（无浏览器）；
# extreme=+服务端渲染截图对比闭环（Playwright 缺失时降级 advanced）
Build3dMode = Literal["standard", "advanced", "extreme"]


class Build3dRequest(BaseModel):
    image_url: str = Field(description="图片地址（data URL 或内网 HTTP）")
    mode: Build3dMode = Field(default="standard", description="构建质量模式：standard/advanced/extreme")


class SpecIssue(BaseModel):
    """审阅/对比阶段产出的单条问题（advanced/extreme 自纠用）"""

    target: str = Field(description="定位路径：aspect / primitives[0].materials[4] / lathes[0].points …")
    problem: str = Field(description="与图片不符之处")
    suggestion: str = Field(description="修正建议")
    severity: Literal["critical", "minor"] = Field(default="minor")


class SpecCritique(BaseModel):
    """审阅/对比阶段出参：passed=true（无 critical）→ 自纠早停"""

    passed: bool = Field(default=False, description="无 critical 问题即 true（自纠收敛）")
    issues: list[SpecIssue] = Field(default_factory=list, max_length=8)
    fidelity: float | None = Field(default=None, ge=0, le=1, description="整体还原度 0-1（仅 extreme 渲染对比时输出）")


class FaceCompletion(BaseModel):
    """缺失面补全建议：LLM 按包装行业规范生成的单个面印刷内容"""

    face: int = Field(ge=0, le=5, description="box 六面索引 [0]=+X [1]=-X [2]=+Y [3]=-Y [4]=+Z正面 [5]=-Z")
    bg: str = Field(default="#ffffff", description="底色 #rrggbb 或 transparent")
    items: list[DecalItem] = Field(default_factory=list, max_length=10)


class FaceCompletionResult(BaseModel):
    """补全阶段出参：只包含需要补全的面（已有面不重复）"""

    faces: list[FaceCompletion] = Field(default_factory=list, max_length=6)


class Build3dResponse(BaseModel):
    """3D 构建响应：spec 为空 = 构建失败，前端回退预设模型库（永不阻断）"""

    spec: Build3dSpec | None = Field(default=None, description="3D spec；None=构建失败回退预设模型")
    model_name: str = Field(default="", description="实际命中模型")
    degraded: bool = Field(default=False)
    error: str = Field(default="", description="失败原因（仅日志/调试）")
    elapsed_ms: int = Field(default=0)
    mode: str = Field(default="standard", description="实际执行的构建模式（extreme 降级时回显 advanced）")
