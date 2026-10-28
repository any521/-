"""services.build3d_service · 3D 模型构建（img2threejs 风格融合）

视觉 LLM 看真实图片 → 输出结构化 spec（几何体组合/颜色/材质/待机动画），
前端 three/specModel.ts 执行器渲染为 THREE.Group（「重建即代码」，无网格文件）。

设计约束：
- 3D 构建是识别链路的锦上添花，任何失败（LLM 不可用/JSON 解析失败/校验失败）
  一律返回 spec=None，由前端回退预设模型库，绝不阻断主流程。
"""
from __future__ import annotations

import base64
import math
import time

from app.core import llm_router
from app.core.logging import get_logger
from app.schemas.verify import (
    Build3dMode, Build3dResponse, Build3dSpec, DecalItem, FaceCompletionResult, FaceMaterial,
    SpecCritique, SpecIssue, SpecPrimitive,
)

# ===== RAG 先验（RAG-04）：数据集统计先验注入 3D 生成链路 =====
# 依赖契约 C2（retriever 由 RAG-02 交付）：未交付/导入失败 → 降级直接读 rag_data/priors_3d.json；
# 任何先验环节失败仅 log.info 跳过（「锦上添花，失败不阻断」），spec=None 回退预设模型库路径不受影响。
try:
    from app.rag import retriever
    _RAG_OK = True
except Exception:
    retriever = None
    _RAG_OK = False

try:
    from rag.priors.loader import match_prior as _match_prior_local
    _PRIORS_OK = True
except Exception:
    _match_prior_local = None
    _PRIORS_OK = False

# 先验注入总开关（默认开启；评估脚本回归对比用，不参与任何 schema/路由）
_PRIOR_ENABLED = True

log = get_logger("build3d_service")

# 底部锚点关键词：包装正面/侧面常见"位于底部"的文字，用于判定 LLM 的 region 坐标系方向
# （LLM 常按数学坐标系 y 向上输出，而渲染器 specModel.ts 是左上原点 y 向下 → 布局上下颠倒）
_BOTTOM_ANCHOR_KEYS = (
    "净含量", "含量", "净重", "规格", "条码", "配料", "营养",
    "保质期", "生产日期", "二维码", "回收", "mL", "L", "kg",
)

# 侧面内容关键词：营养表/配料表/条码等只出现在侧面的印刷元素
_SIDE_KEYS = ("营养", "配料", "条码", "二维码", "回收", "生产日期", "保质期", "成分", "热量")


def _auto_flip_face(face) -> bool:
    """自动校正单个面的布局方向：若底部锚点文字（净含量/规格/条码等）的 y 反而小于
    其他元素的中位 y，说明 LLM 用了数学坐标系（y 向上）→ 将全部元素 y 翻转（1-y-h）。
    返回 True=已翻转。无锚点或顺序正常则不动作（避免误翻）。
    两类判定：① 有对照元素——锚点需显著高于其他元素中位（差 >0.12）才翻；② 整面都是
    锚点类（纯营养表面/单个净含量元素，无对照）——锚点 y<0.5 即翻（锚点在面上半部）。
    锚点文字（净含量/营养/配料/条码）在包装面上几乎总在底部，先验成立。"""
    items = face.items
    if len(items) < 1:
        return False
    bottoms = [it for it in items if any(k in (it.text or "") for k in _BOTTOM_ANCHOR_KEYS)]
    others = [it for it in items if it not in bottoms]
    if not bottoms:
        return False
    y_bottom = min(it.region[1] for it in bottoms)
    if others:
        y_others_med = sorted(it.region[1] for it in others)[len(others) // 2]
        if y_bottom < y_others_med - 0.12:
            for it in items:
                it.region[1] = round(1.0 - it.region[1] - it.region[3], 4)
            return True
        return False
    # 整面都是锚点类（无对照元素）：锚点在面上半部 → 数学坐标系 → 翻
    if y_bottom < 0.5:
        for it in items:
            it.region[1] = round(1.0 - it.region[1] - it.region[3], 4)
        return True
    return False


def _side_score(face) -> int:
    """面中侧面类元素（营养/配料/条码等）计数"""
    return sum(1 for it in (face.items or []) if any(k in (it.text or "") for k in _SIDE_KEYS))


def _normalize_materials(mats: list | None) -> list | None:
    """materials 数组归一化到六面索引（渲染器 specModel.ts 按索引贴面）。

    保守策略（历史坑：对 6 元素数组做全量语义重排会打乱本已正确的六面——侧面品牌
    图案/装饰条不带"营养/配料"关键词会被误判为非侧面，整面被打散挪位）：
    - 6 元素数组：默认保持原索引，只修两类明确错误——① 正面[4] 空或纯侧面类时，
      主视觉面（非侧面元素最多）换入 [4]；② 顶[2]/底[3] 含侧面类元素 → 挪到空侧面槽。
    - 压缩数组（<6，LLM 只写有内容的面）：按内容语义重排，主视觉→[4]（纯侧面则→[0]），
      侧面类→[0]/[1]（保持原相对顺序），其余→[5]/[3]/[2]。
    返回 None 表示无需归一化（数组为空）。"""
    if not mats:
        return mats
    faces = [(i, m) for i, m in enumerate(mats) if m is not None]
    if not faces:
        return mats
    non_side = [(i, m) for i, m in faces if _side_score(m) == 0]
    main = max(non_side or faces, key=lambda f: len(f[1].items or []))

    if len(mats) == 6:
        out: list = list(mats)
        changed = False
        front = out[4]
        if front is None or (front is not None and (not front.items or _side_score(front) > 0)):
            # ① 正面[4] 空/纯侧面 → 主视觉换入（原 [4] 内容回填到主视觉原槽位）
            if main[0] != 4:
                out[4], out[main[0]] = main[1], out[4]
                changed = True
        for i in (2, 3):  # ② 顶/底面含侧面类 → 挪到空侧面槽 [0]/[1]
            m = out[i]
            if m is not None and _side_score(m) > 0:
                for slot in (0, 1):
                    if out[slot] is None:
                        out[slot], out[i] = m, None
                        changed = True
                        break
        return out if changed else mats

    # 压缩数组（<6）：按内容语义重排（保持原相对顺序）
    out = [None] * 6
    if non_side:
        out[4] = main[1]  # 主视觉 → 正面 +Z
    else:
        out[0] = main[1]  # 纯侧面内容 → 右侧 +X
    for i, m in faces:
        if i == main[0]:
            continue
        if _side_score(m) > 0:
            slot = i if i in (0, 1) and out[i] is None else None
            if slot is None:
                slot = 0 if out[0] is None else (1 if out[1] is None else None)
            if slot is not None:
                out[slot] = m
        else:
            for slot in (5, 3, 2, 1, 0):
                if out[slot] is None:
                    out[slot] = m
                    break
    return out

_SPEC_PROMPT = (
    "你是程序化 3D 建模师（img2threejs 风格，贴图优先：先做 UV 规划再建模）。"
    "根据图片中的包装/物品重建 3D 模型，输出 JSON："
    '{"item":"物品名",'
    '"aspect":[宽,高,深](整体包围盒比例，高=1，对照实物校准),'
    '"primitives":[{"type":"box|cylinder|sphere|cone|plane",'
    '"size":[宽,高,深](box/plane 用), "radius":半径, "height":高(cylinder/cone 用),'
    '"pos":[x,y,z], "rot":[rx,ry,rz], "color":"#rrggbb",'
    '"roughness":0-1, "metalness":0-1, "transparent":true/false, "opacity":0-1,'
    '"surface":"none|paper|plastic|metal|glass",'
    '"materials":[{"bg":"#rrggbb","items":[{"type":"text|circle|rect|stripe","text":"文字",'
    '"color":"#rrggbb","region":[x,y,宽,高](0-1左上原点),"fontSize":0.6,"border":true/false}]}],'
    '"label":{"bg":"#rrggbb","items":[同上]}},...],'
    '"lathes":[{"points":[[半径,高度],...]（y 递增：底部→顶部，杯口在 y 最大处且半径≥底部，'
    '3-5 点平滑轮廓表达杯壁曲线；杯/瓶/罐等旋转对称体优先用 lathe，禁止实心 box/cylinder 冒充；'
    'lathes 数组内只允许这种带 points 的车削体对象，禁止放入 cylinder 等几何体对象'
    '（几何体一律放 primitives，type 字段只出现在 primitives 条目中））, '
    '"pos":[x,y,z], "rot":[rx,ry,rz], "color":"#rrggbb",'
    '"surface":"none|paper|plastic|metal|glass", "label":{...}},...],'
    '"tubes":[{"curve":[[x,y,z],...]（全局坐标，与几何体同一坐标系，原点=底部中心，整体高=1），'
    '"radius":管道半径（吸管 0.02-0.05）, "pos":[0,0,0], "color":"#rrggbb",'
    '"surface":"none|paper|plastic|metal|glass"},...],'
    '"decals":[{"text":"表面小字", "shape":"text|circle|rect|stripe", "color":"#rrggbb",'
    '"pos":[x,y,z], "size":[宽,高], "fontSize":字高},...],'
    '"tick":"float|sway|spin|none"}。'
    "【工作流，必须按序执行】"
    "⓪ 分层观察（先观察后建模，观察过程写入思考不必输出）："
    "宏观=整体形态与宽高深比例（对照实物照片估计）；中观=部件分解（主体/封口/瓶盖/提手/吸管等）；"
    "微观=贴图文字/颜色/材质细节。然后锁定 3-5 个决定辨认度的关键特征"
    "（如利乐砖顶部折翼、玻璃瓶半透明、易拉罐拉环盖、纸杯微锥杯身），"
    "后续建模与贴图优先保证这些关键特征不丢失、不变形。"
    "① 比例校准：先定 aspect=[宽,高,深]（高=1，对照实物照片用参考网格估计）。参考："
    "利乐砖/牛奶盒=瘦高长方体≈[0.62,1,0.38]（200mL 常见，长宽高约 2.6:1.6:1）；"
    "矮胖纸盒/果汁盒≈[0.8,1,0.45]；易拉罐≈[0.42,1,0.42]；"
    "塑料瓶≈[0.55,1,0.55]；玻璃瓶≈[0.45,1,0.45]（瓶颈窄、瓶身圆）；"
    "纸杯/奶茶杯≈[0.7,1,0.7]（上宽下窄微锥）；泡面碗≈[0.85,1,0.85]；"
    "玻璃杯/马克杯≈[0.75,1,0.75]（敞口、上宽下窄微锥）。"
    "盒体类宽高比不得小于 0.5、不得大于 1.2；所有几何体尺寸必须与 aspect 一致，禁止整体拉长/压扁。"
    "② 贴图规划（先规划 UV 再建几何体；box 六面索引固定："
    "[0]=+X右侧 [1]=-X左侧 [2]=+Y顶面 [3]=-Y底面 [4]=+Z正面 [5]=-Z背面）："
    "主视觉面=正面[4]：品牌 logo+产品名+主色块+图案+徽章+净含量，自上而下按图片真实布局；"
    "侧面[0]/[1]：营养成分表/配料表/条形码/装饰条；背面[5]：品牌文案/二维码/回收标志；"
    "顶面[2]：纸盒类=素白封口面（无印刷或仅小图案），瓶罐类=盖/封口；底面[3]：素色。"
    "图中不可见的面按包装行业规范补全（行业标配，不算臆造）：纸盒/利乐包侧面=营养成分表"
    "（表格感：多行小字+数字）+配料表（配料：…）+条形码（黑色竖条+下方数字）、背面=品牌口号"
    "+二维码/回收标志/生产信息小字；瓶罐背面=营养/配料/条码。颜色用包装主色调近似，"
    "文字用已识别信息（品名/品牌/净含量）+通用格式，具体数值无法确定时用占位（如 蛋白质 Xg），禁止编造具体数字。"
    "region 坐标系：左上原点，y 值越大越靠下（顶部 y≈0.05，底部 y≈0.9）！"
    "正面元素自上而下典型顺序（y 递增）：品牌 Logo(y≈0.05) → 弧形/斜形色带(y≈0.2) "
    "→ 产品名大字号美术字(y≈0.35) → 插画/图案(y≈0.55) → 徽章/标签(y≈0.7) → 净含量/规格小字(y≈0.88)；"
    "每项按真实位置与颜色还原，禁止省略主视觉元素；"
    "复杂图形（弧带/徽章/花式字体）用 text+circle+rect+stripe 组合近似，颜色取真实像素。"
    "文字 OCR 近似识别（品牌名/产品名/净含量等短文字），不要臆造大段文字；"
    "关键文字必须逐字完整识别（禁止漏字/缩写/省略，如「伊利纯牛奶」禁止写成「伊利牛奶」）。"
    "③ 结构建模：只添加图片中真实存在的结构部件（封口、瓶口、提手、吸管、顶部折翼压痕等），"
    "顶部没有吸管就不要加管状物；每个结构部件必须能在图片中找到对应实物；不确定的结构宁可不加。"
    "零件拼接规则：吸管等插入件必须与主体贴合——曲线起点落在主体表面（吸管插顶面），"
    "插入段伸入主体内 0.05-0.1，禁止悬浮/脱离/整根埋在主体内部；吸管/提手用 tubes 表示，"
    "curve 用全局坐标，禁止把插入件混进 box 几何体。"
    "零件尺寸比例（对照实物，禁止失调）：瓶盖/瓶嘴高度≈主体高的 0.05-0.15、直径略大于瓶口直径（约 1.2-1.5 倍）且不超过主体直径；"
    "吸管长度≈主体高的 1.2-1.5 倍（插入部分+露出部分）、半径 0.02-0.05；"
    "提手/把手厚度远小于主体；所有零件尺寸必须小于主体尺寸（主体=最大几何体）。"
    "（注：印刷内容补全≠结构臆造——结构禁止虚构，印刷按行业规范补全。）"
    "④ 材质模拟：surface 按实物设置——纸盒/纸杯=paper（轻微反光+纤维纹理）、"
    "塑料瓶/膜=plastic、铝罐/金属盖=metal、玻璃瓶/杯=glass（半透明）；其余用 none。"
    "透明物品专项：玻璃/透明塑料用 surface=glass + transparent=true + 实物颜色"
    "（颜色取真实像素，无色玻璃≈#e8f4f4，有色玻璃如实色即可，禁止黑色实心块）；"
    "玻璃的透明感由材质自动实现，opacity 范围 0.3-1.0（0.3=半透明磨砂，0.5=半透明有色玻璃，0.8-1.0=厚玻璃）；"
    "想表达透明的几何体必须 transparent=true（只给低 opacity 不给 transparent 会渲染成实心块）；"
    "杯类主体用 lathes 空心旋转体（杯口朝上、y 最大处），轮廓点 8-14 个表达杯壁/瓶肩曲线"
    "（精确曲线需更多点：底部收口 2-3 点、瓶身 3-4 点、肩部过渡 2-3 点、颈部 2-3 点、瓶口 1-2 点）；"
    "液体内容物可用半透明深色 cylinder 内嵌（opacity 0.5-0.8），杯子本身仍是 glass。"
    "PET 塑料瓶专项（矿泉水/饮料瓶）："
    "【颜色分配规则——必须严格遵守】"
    "1) 瓶身本体（最大的 lathe）= 近乎无色透明，颜色 #f8f8f8 或 #f5f5f5，surface=glass，transparent=true，opacity=0.35-0.45"
    "2) 瓶身标签（覆盖在瓶身中部的带状区域）= 图片中的实际颜色（如蓝色标签用 #1565c0），surface=plastic，不透明；"
    "   label.bg 必须设置为标签主色，禁止使用透明、none、#00000000；标签顶部如有雪山/风景/图案，"
    "   用浅色 rect/stripe 区域（如 #e3f2fd、#ffffff）近似表示，不要省略。"
    "3) 瓶盖 = 白色不透明 cylinder，surface=plastic，radius 约为瓶口半径的 1.2-1.5 倍，height 0.08-0.12，位于瓶口上方。"
    "4) 内部液体（可选）= 半透明浅色（水=#e0f0ff），opacity 0.2-0.4，radius 比瓶身小 0.02-0.05，高度不超过实际液面。"
    "【禁止】将标签颜色（蓝色/绿色/红色）应用到瓶身本体！瓶身本体必须是近白色透明！"
    "瓶身 lathe 轮廓必须包含底部、瓶身、肩部收窄、颈部、瓶口等多段曲线，不能是简单 2 点直线圆柱；"
    "瓶身下半部分常有水平加强筋/凹槽，在 lathe 轮廓中用交替半径点表示（如 r=0.28→0.26→0.28→0.26）。"
    "【标签 label 布局——必须严格遵守，违反会导致文字被遮挡或旋转 90° 失真】"
    "label 画布是横向环绕展开的带状画布（宽 1024 × 高 256，4:1 横向），贴在瓶身中部标签带一周。"
    "标签带高度约占瓶身的 35%-55%，因此所有 label items 的 y 坐标应落在 0.1-0.9 之间，不要顶到瓶口或瓶底。"
    "标签典型自上而下布局："
    "  顶部图案/雪山/天空（浅色 stripe/rect，如 #e3f2fd，y≈0.05-0.25）；"
    "  品牌/产品名大字号白字（放在标签主色背景上，y≈0.30-0.45，不得压在浅色图案上）；"
    "  拼音/英文（y≈0.45-0.55）；"
    "  说明文案/净含量（y≈0.65-0.85）。"
    "白色文字必须位于深色/主色背景区域，禁止放在浅色图案上导致看不见。"
    "label 上的所有文字必须横排（左→右阅读，像书页一行字），禁止竖排堆叠！"
    "正确写法：一整条文字作为单个 item 输出，region 是宽矮的横向矩形（宽 > 高），如"
    "「天然水」= 一个 item：text=\"天然水\", region=[0.25, 0.35, 0.5, 0.3]（宽 0.5 高 0.3，横向）。"
    "错误写法（禁止！）：把每个字拆成单独 item 纵向堆叠，如"
    "「天」region=[0.45,0.1,0.1,0.25] + 「然」region=[0.45,0.4,0.1,0.25] + 「水」region=[0.45,0.7,0.1,0.25]"
    "——这样画布上字符纵向排列，环绕到瓶身后文字会被旋转 90° 变成纵向堆叠，严重失真。"
    "拼音/英文同理：「TIANRANSHUI」必须作为单个 item 横排输出，禁止拆成 T/I/A/N/... 单字母堆叠。"
    "label region 坐标系：左上原点，y 增大向下；x 轴对应瓶身圆周方向（横向），y 轴对应标签带高度方向（纵向）。"
    "多条文字（如品名 + 拼音）按真实布局上下排列，每条都是横向矩形 region，y 递增即可；"
    "标签文字 fontSize 应占 region 高度的 0.5-0.8，品名最大，保证 Canvas 上清晰可读。"
    "有机不规则体专项（香蕉皮/菜叶/果皮/剩饭等厨余）：不追求几何精确，追求颜色与形态神似——"
    "用 sphere/lathe 组合 + 表面色块近似轮廓，允许 stylized 低多边形，禁止硬套 box 冒充。"
    "⑤ 主视觉自检（输出前逐项核对，缺失立即补）："
    "a) 品牌 logo/主标识是否在正面[4]；b) 产品名是否在正面[4]且为该面最大字号；"
    "c) 主色块/装饰带位置与颜色是否符合图片；d) 徽章/标签/净含量是否在正面[4]下部；"
    "e) 侧面[0]/[1]是否有营养/配料/条码区；f) 主视觉是否误印到顶面[2]（禁止）；"
    "g) aspect 是否符合实物比例（牛奶盒瘦高、不可矮胖）；"
    "h) 布局方向（region 左上原点）：logo 必须 y 小（顶部）、净含量必须 y 大（底部），禁止上下颠倒；"
    "i) 文字完整性：品牌名/产品名/净含量逐字完整无漏字缩写（如「伊利纯牛奶」禁止写成「伊利牛奶」）；"
    "j) 透明物品：玻璃/透明塑料 surface=glass + transparent=true + 实物色（禁止黑色实心块），"
    "opacity 0.3-1.0（半透明磨砂/有色玻璃用 0.3-0.7，厚玻璃用 0.8-1.0）；"
    "杯/瓶必须为空心旋转体（杯口朝上在 y 最大处）；"
    "k) 负面约束汇总（输出前最后核对）：禁止臆造结构部件；禁止透明件建成黑色实心块；"
    "禁止 region 布局上下颠倒；禁止品牌名/产品名/净含量漏字缩写；禁止主视觉误印顶面[2]；"
    "禁止整体比例拉长/压扁；禁止 label 文字竖排堆叠（每个字单独 item 纵向排列会导致环绕到瓶身后旋转 90° 失真）。"
    "【约束】primitives 5-12 个；每个 box 至少给正面[4] materials；"
    "materials/label 每面 1-10 个元素；尺寸归一化：整体高=1、原点在底部中心；"
    "颜色/文字/图案全部取自真实图片，禁止臆造；只输出物品本体，不要背景/地面。"
    "物品不确定或图片无效时 primitives/lathes/tubes 输出空数组。"
    "item 必须是单个字符串（品牌与品名合并为一个字符串，如「伊利纯牛奶」），"
    "禁止输出对象或多字段（如 {\"brand\":...}、{\"name\":...}、{\"product_name\":...}）；"
    "品牌/品名/营养成分只作为 item 字符串的一部分或各面印刷内容，不单独成顶层字段。"
    "materials/label 元素 fontSize 取值 0.05-1（文字高度占 region 高度比例），"
    "禁止 0 或小于 0.05 的值。"
)

# 补全阶段 prompt：单次调用仍可能因"禁止臆造"保守不补全不可见面，
# 由本阶段（纯文本、专职版式专家）按包装行业规范生成缺失面内容
_COMPLETE_PROMPT = (
    "你是包装行业版式专家。任务：为 3D 包装模型补全图片中不可见的印刷面。"
    "输入：物品信息 + 已识别面内容摘要 + 需要补全的面索引。"
    "【行业规范（按包装类型套用）】"
    "纸盒/利乐包/硬盒：侧面=营养成分表（表格感：多行小字+数字，如 能量/蛋白质/脂肪/碳水/钠）"
    "+配料表（配料：…）+条形码（黑色竖条组+下方数字）；背面=品牌口号/二维码（方形黑白块）"
    "/回收标志/生产信息小字；顶面=素白封口。"
    "瓶/罐/杯：背面=营养成分/配料/条码；标签区环绕。"
    "【规则】颜色用包装主色调（摘要中已提取）近似；文字用已识别信息（品名/品牌/净含量）；"
    "具体数值无法确定时用占位（如 蛋白质 Xg/100mL），禁止编造具体数字；"
    "已有面内容不重复输出（只输出需要补全的面）。"
    "region 坐标系：左上原点，y 值越大越靠下（顶部 y≈0.05，底部 y≈0.9）。"
    "输出 JSON：{\"faces\":[{\"face\":索引0-5,\"bg\":\"#rrggbb\",\"items\":[元素...]}]}，"
    "items 元素格式：{\"type\":\"text|circle|rect|stripe\",\"text\":\"文字\","
    "\"color\":\"#rrggbb\",\"region\":[x,y,w,h]（数组，0-1 左上原点，禁止对象形式）,"
    "\"fontSize\":0.05-1（region 高度比例，禁止像素值）,\"border\":true/false}；"
    "非边框元素省略 border 字段，不要输出 null；region 必须是 [x,y,w,h] 数组。"
)


def _face_summary(spec: Build3dSpec) -> str:
    """提取已识别信息摘要（物品/比例/各面文字与主色），供补全阶段参考"""
    parts = [f"物品={spec.item}", f"aspect={spec.aspect}"]
    for p in spec.primitives:
        if not p.materials:
            continue
        for i, fm in enumerate(p.materials):
            if fm is None or not fm.items:
                continue
            texts = [it.text for it in fm.items if it.text]
            colors = [it.color for it in fm.items if it.color]
            parts.append(f"面[{i}]=({'|'.join(texts) if texts else '无色块'}; 主色:{','.join(colors[:3])})")
    return "；".join(parts)


def _missing_faces(spec: Build3dSpec) -> tuple[SpecPrimitive, list[int]] | None:
    """找主 box（含正面[4]主视觉的 box）及其空面索引。
    无主 box（纯圆柱/贴花等）→ None（不触发补全）。"""
    for p in spec.primitives:
        if not p.materials or len(p.materials) < 5:
            continue
        front = p.materials[4]
        if front is None or not front.items:
            continue  # 正面无内容=识别失败，不补
        missing = [i for i in (0, 1, 2, 5) if i >= len(p.materials) or p.materials[i] is None
                   or not (p.materials[i].items or (p.materials[i].bg or "").lower() != "transparent")]
        return p, missing
    return None


def _complete_missing_faces(spec: Build3dSpec, box: SpecPrimitive, missing: list[int]) -> int:
    """补全阶段：调用版式专家生成缺失面印刷内容并合并（只填空面，失败静默跳过）。
    返回补全的面数。"""
    if not missing:
        return 0
    try:
        result = llm_router.invoke(
            capability="visual_verify",
            messages=[
                {"role": "system", "content": _COMPLETE_PROMPT},
                {"role": "user", "content": (
                    f"需要补全的面索引={missing}；{_face_summary(spec)}"
                )},
            ],
            response_model=FaceCompletionResult,
            context={"scene": "3d_complete"},
            timeout_seconds=45,
        )
        if result.provider == "mock":
            log.info("3D 补全阶段命中 mock，跳过（联调占位不适用）")
            return 0
        merged = 0
        mats = list(box.materials or [])
        while len(mats) < 6:
            mats.append(None)
        for fc in result.parsed.faces:
            if fc.face not in missing or not fc.items:
                continue
            _auto_flip_face(fc)  # 补全面同样兜底坐标系方向
            mats[fc.face] = FaceMaterial(bg=fc.bg, items=fc.items)
            merged += 1
        if merged:
            box.materials = mats
        return merged
    except Exception as e:  # 补全是锦上添花：任何失败都不阻断
        log.info(f"3D 补全阶段跳过: {e}")
        return 0


def _vec3(v) -> tuple[float, float, float]:
    """尺寸/位置三元组兜底：LLM spec 可能给 2 值（plane 等无厚度）或缺位，补 0 防解包崩溃。"""
    s = list(v) if v else []
    while len(s) < 3:
        s.append(0.0)
    return s[0], s[1], s[2]


def _geo_aabb(geo) -> tuple[float, float, float, float, float, float, float] | None:
    """几何体 AABB + 体积（渲染器中心原点语义；lathe 圆柱近似）。
    返回 (xmin, ymin, zmin, xmax, ymax, zmax, volume) 或 None（无尺寸）。"""
    if hasattr(geo, "size") and geo.size:
        # plane 等几何体 size 可能只有 [w, h] 两个值 → 补 0
        s = list(geo.size)
        while len(s) < 3:
            s.append(0.0)
        sx, sy, sz = s[0], s[1], s[2]
        if sx <= 0 or sy <= 0:
            return None
        hx, hy, hz = sx / 2, sy / 2, sz / 2 if sz > 0 else sx / 2
        vol = sx * sy * max(sz, sx)
    elif hasattr(geo, "points") and geo.points:
        r = max(p[0] for p in geo.points)
        ys = [p[1] for p in geo.points]
        hx = hz = r
        hy = (max(ys) - min(ys)) / 2
        vol = math.pi * r * r * hy * 2
    elif hasattr(geo, "radius") and geo.radius is not None:
        r = geo.radius
        hx = hz = r
        hy = geo.height / 2 if geo.height is not None else r
        vol = 4 / 3 * math.pi * r ** 3 if geo.height is None else math.pi * r * r * geo.height
    else:
        return None
    x, y, z = _vec3(geo.pos)
    return (x - hx, y - hy, z - hz, x + hx, y + hy, z + hz, vol)


# 锚点高度合格区间（相对 aspect 高=1）：主体件高度应与整体比例相符；
# 瓶盖/瓶嘴/吸管等附件常被 LLM 建成矮粗（≤0.55）或细高（≥1.8），体积又大，
# 若不排除会把锚点抢走导致钳制整体失效。
_ANCHOR_HEIGHT_RATIO_MIN = 0.55
_ANCHOR_HEIGHT_RATIO_MAX = 1.8


def _max_anchor(spec: Build3dSpec):
    """吸管拼接的吸附基准（兜底）：体积最大的 box/lathe/cylinder/cone/sphere，
    优先「高度与 aspect 高相符」的件（排除被 LLM 建成矮粗/细高的附件——瓶盖/装饰件）。

    「叠附排除」启发式：LLM 把瓶盖/装饰件建得比主体还大时（用户实测场景），
    体积最大者会错误地成为锚点，导致零部件钳制整体失效。按体积降序考察，
    先过滤高度比例异常的附件，再从剩余件中排除与已接受者「纵向+水平重叠」的叠附件。
    与 _missing_faces 解耦——识别出的 box 正面无主视觉时也能拼接吸管。"""
    candidates = []
    for p in spec.primitives:
        box = _geo_aabb(p)
        if box is not None:
            candidates.append((box, p))
    for l in spec.lathes:
        box = _geo_aabb(l)
        if box is not None:
            candidates.append((box, l))
    if not candidates:
        return None
    ref_h = spec.aspect[1] if spec.aspect and spec.aspect[1] > 0 else 1.0
    # 体积降序；体积并列时优先 box/lathe（主体语义优先）
    candidates.sort(key=lambda c: (c[0][6], c[1].type not in ("box", "lathe"))
                    if hasattr(c[1], "type") else (c[0][6], True), reverse=True)
    accepted: list[tuple] = []
    for box, geo in candidates:
        hgt = box[4] - box[1]
        ratio = hgt / ref_h if ref_h > 0 else 1.0
        if ratio < _ANCHOR_HEIGHT_RATIO_MIN or ratio > _ANCHOR_HEIGHT_RATIO_MAX:
            continue  # 高度比例异常：附件嫌疑（瓶盖矮粗/瓶嘴细高），不当锚点
        xmin, ymin, zmin, xmax, ymax, zmax, _ = box
        is_attached = False
        for abox, _a in accepted:
            # 纵向重叠（瓶盖/装饰叠在主体顶面/侧面）+ 水平重叠（x/z 区间有交集）
            v_overlap = ymin < abox[4] and ymax > abox[1]
            h_overlap = (xmin < abox[3] and xmax > abox[0]) and (zmin < abox[5] and zmax > abox[2])
            if v_overlap and h_overlap:
                is_attached = True
                break
        if not is_attached:
            accepted.append((box, geo))
            return geo
    # 全部高度异常（整体畸形）→ 体积最大兜底
    return candidates[0][1]


def _snap_tubes(spec: Build3dSpec, anchor) -> int:
    """吸管等插入件的贴面拼接校正（保守启发式，任何语义不明都不动）。

    常见失败：LLM 把 tube 的 curve 算飘（悬浮在主体上方/脱离表面），渲染器原样渲染。
    校正规则——① 主体顶面优先：tube 任一端点距顶面垂直距离 ≤0.45 且水平投影落在顶面
    范围内 → 平移到该端点插入顶面 0.05（吸管插顶面是最常见形态，先验正确）；
    ② 否则任一端点距主体表面 ≤1.2 → 吸附最近表面点（提手/把手类）；③ 已贴合（<0.05）
    /过远（>1.2）/tube 带旋转（rot≠0，旋转坐标系下端点世界坐标≠pos+curve，吸附会错位）
    /粗件（radius 超过主体最小半尺寸 1/3）→ 不动。
    坐标语义与渲染器 specModel.ts 完全一致：mesh.position=pos、几何体以自身原点为中心，
    世界坐标 = pos + curve；因此 box 顶面 y = pos.y + 半高（BoxGeometry 中心原点，不是
    底部中心！），tube 距离/吸附用世界坐标（curve+pos），位移只施加到 pos（世界坐标整体
    平移，对全局/局部两种 curve 语义都正确，禁止 curve+pos 双重平移）。
    anchor 为 box（AABB 精确）或 lathe（圆柱近似：半径=轮廓最大 r，高度=y 范围）。
    返回校正的 tube 数。"""
    if not spec.tubes or anchor is None:
        return 0
    # 统一提取锚点几何参数（AABB 半尺寸 + 中心 + 顶面 y，均按渲染器中心原点语义）
    if hasattr(anchor, "size") and anchor.size:
        sx, sy, sz = _vec3(anchor.size)
        hx, hy, hz = sx / 2, sy / 2, sz / 2
        bx, by, bz = _vec3(anchor.pos)
        top_y = by + hy  # BoxGeometry 中心在 pos → 顶面 = pos.y + 半高
        cx, cy, cz = bx, by, bz
    elif hasattr(anchor, "points") and anchor.points:
        ys = [p[1] for p in anchor.points]
        r = max(p[0] for p in anchor.points)
        ymin, ymax = min(ys), max(ys)
        bx, by, bz = _vec3(anchor.pos)
        top_y = by + ymax
        hx, hy, hz = r, (ymax - ymin) / 2, r
        cx, cy, cz = bx, by + (ymin + ymax) / 2, bz
    elif hasattr(anchor, "radius") and anchor.radius is not None and anchor.height is not None:
        # cylinder/cone：中心原点几何体，顶部 = pos.y + 半高（与渲染器一致）
        r = anchor.radius
        h = anchor.height
        bx, by, bz = _vec3(anchor.pos)
        top_y = by + h / 2
        hx, hy, hz = r, h / 2, r
        cx, cy, cz = bx, by, bz
    elif hasattr(anchor, "radius") and anchor.radius is not None:
        r = anchor.radius
        bx, by, bz = _vec3(anchor.pos)
        top_y = by + r  # sphere 中心在 pos → 顶点 = pos.y + 半径
        hx, hy, hz = r, r, r
        cx, cy, cz = bx, by, bz
    else:
        return 0

    def _surface_dist(p: list) -> float:
        x, y, z = p
        dx = max(abs(x - bx) - hx, 0.0)
        dy = max(abs(y - cy) - hy, 0.0)
        dz = max(abs(z - bz) - hz, 0.0)
        return math.sqrt(dx * dx + dy * dy + dz * dz)

    def _nearest_surface(p: list) -> tuple:
        return (max(bx - hx, min(bx + hx, p[0])),
                max(cy - hy, min(cy + hy, p[1])),
                max(bz - hz, min(bz + hz, p[2])))

    snapped = 0
    for t in spec.tubes:
        pts = [list(v) for v in t.curve]
        if len(pts) < 2:
            continue
        if any(abs(v) > 0.01 for v in (t.rot or [])):
            continue  # 带旋转：端点世界坐标 ≠ pos+curve，吸附会错位
        if t.radius > min(hx, hy, hz) / 3:
            continue  # 粗件（主体类），不吸附
        px, py, pz = _vec3(t.pos)
        # 世界坐标 = pos + curve（与渲染器 place() 一致）
        world = [[p[0] + px, p[1] + py, p[2] + pz] for p in pts]
        ends = (world[0], world[-1])
        d_top = [abs(e[1] - top_y) for e in ends]
        k = 0 if d_top[0] <= d_top[1] else 1
        if d_top[k] < 0.05:
            continue  # 已贴合顶面（插入 0~0.05），不动
        if d_top[k] <= 0.45 and abs(ends[k][0] - bx) <= hx and abs(ends[k][2] - bz) <= hz:
            # ① 顶面优先（端点水平投影须在顶面范围内，防侧把手/横跨件被误拖）
            dx, dy, dz = 0.0, (top_y - 0.05) - ends[k][1], 0.0
        else:
            d_surf = [_surface_dist(e) for e in ends]
            k = 0 if d_surf[0] <= d_surf[1] else 1
            d = d_surf[k]
            if d < 0.12 or d > 1.2:
                continue
            near = _nearest_surface(ends[k])
            dx = near[0] - ends[k][0]
            dy = near[1] - ends[k][1]
            dz = near[2] - ends[k][2]
            # 沿"表面→主体中心"方向伸入 0.05（插入段在主体内）
            inward = (cx - near[0], cy - near[1], cz - near[2])
            il = math.sqrt(sum(v * v for v in inward)) or 1.0
            dx += inward[0] / il * 0.05
            dy += inward[1] / il * 0.05
            dz += inward[2] / il * 0.05
        # 世界位移只施加到 pos（世界=pos+curve；整体平移，两种 curve 语义都正确，
        # 禁止 curve+pos 双重平移）
        t.pos = [px + dx, py + dy, pz + dz]
        snapped += 1
    return snapped


def _clamp_attachments(spec: Build3dSpec, anchor) -> int:
    """零部件尺寸钳制（吸管/把手/瓶盖等附加件过大兜底）。

    LLM 生成 spec 时附加件尺寸常与主体比例失调（如吸管 radius=0.3 而杯宽仅 0.8）。
    以吸附锚点为主体基准，把非锚点几何体钳制到锚点尺寸的合理比例内——
    tube 半径 ≤ 截面特征*0.1 且 ≤0.05（提示词规范 0.02-0.05 上限），tube 总长 > 锚点特征*2 → 缩到 2 倍
    （吸管不应比主体高+宽之和还长，防止「吸管直插天」）；
    box 任一边 > 锚点对应边 1.5 倍 → 整体等比缩放（保持形状）；
    cylinder/cone/sphere 半径 > 锚点截面特征 0.5 倍 → 钳制，cylinder/cone 高 > 锚点高 1.5 倍 → 钳制；
    lathe 最大轮廓半径 > 锚点截面特征 0.5 倍 → 轮廓半径方向等比缩放；
    lathe 高度 > 锚点高 2 倍 → 高度方向等比缩放（瓶盖/瓶嘴等细高件超高兜底）。
    保守原则：锚点缺失/无尺寸一律不动；只改尺寸不改 pos/curve（不影响贴面拼接）。
    返回修正的几何体数。"""
    if anchor is None:
        return 0
    if hasattr(anchor, "size") and anchor.size:
        aw, ah, ad = _vec3(anchor.size)
        a_sect = max(aw, ad)  # 锚点截面特征尺寸（宽/深较大者）
    elif hasattr(anchor, "points") and anchor.points:
        a_sect = 2 * max(p[0] for p in anchor.points)
        ah = max(p[1] for p in anchor.points) - min(p[1] for p in anchor.points)
        aw = ad = a_sect  # 圆柱近似：截面宽=深=直径
    elif hasattr(anchor, "radius") and anchor.radius is not None:
        # cylinder/cone/sphere 锚点：截面直径 = 2r，高 = height（sphere 取 2r）
        a_sect = 2 * anchor.radius
        ah = anchor.height if anchor.height is not None else a_sect
        aw = ad = a_sect
    else:
        return 0
    if a_sect <= 0 or ah <= 0:
        return 0
    fixed = 0

    # ① tube（吸管/弯管）：半径钳到截面*0.1 与 0.05 的较小者（保底 0.025 防缩没）
    for t in spec.tubes:
        cap = min(max(0.025, a_sect * 0.1), 0.05)
        if t.radius > cap:
            log.info(f"3D 零部件钳制 tubes radius {t.radius:.3f} → {cap:.3f}（锚点截面 {a_sect:.2f}）")
            t.radius = cap
            fixed += 1
        # 长度钳制：总长 > 锚点特征*2（高与截面较大者）→ 等比缩 curve（保持形状与方向）
        if t.curve and len(t.curve) >= 2:
            seg = [math.dist(t.curve[i], t.curve[i + 1]) for i in range(len(t.curve) - 1)]
            total = sum(seg)
            max_len = max(ah, a_sect) * 2
            if total > max_len and total > 0:
                k = max_len / total
                curve = [[round(p[0] * k, 4), round(p[1] * k, 4), round(p[2] * k, 4)] for p in t.curve]
                log.info(f"3D 零部件钳制 tubes 长度 {total:.3f} → {max_len:.3f}（锚点 高{ah:.2f}/截面{a_sect:.2f}）")
                t.curve = curve
                fixed += 1

    for p in spec.primitives:
        if p is anchor:
            continue
        if p.type == "box" and p.size:
            w, h, d = _vec3(p.size)
            ratio = max(w / (aw * 1.5), h / (ah * 1.5), d / (ad * 1.5), 1.0)
            if ratio > 1.0:
                log.info(f"3D 零部件钳制 box size {p.size} → "
                         f"{[round(v / ratio, 3) for v in p.size]}（锚点 {anchor.size}）")
                p.size = [round(v / ratio, 3) for v in p.size]
                fixed += 1
        elif p.type in ("cylinder", "cone") and p.radius is not None:
            changed = False
            if p.radius > a_sect * 0.5:
                log.info(f"3D 零部件钳制 {p.type} radius {p.radius:.3f} → {a_sect * 0.5:.3f}")
                p.radius = a_sect * 0.5
                changed = True
            if p.height is not None and p.height > ah * 1.5:
                log.info(f"3D 零部件钳制 {p.type} height {p.height:.3f} → {ah * 1.5:.3f}")
                p.height = ah * 1.5
                changed = True
            if changed:
                fixed += 1
        elif p.type == "sphere" and p.radius is not None and p.radius > a_sect * 0.5:
            log.info(f"3D 零部件钳制 sphere radius {p.radius:.3f} → {a_sect * 0.5:.3f}")
            p.radius = a_sect * 0.5
            fixed += 1

    # ② lathe（瓶盖/旋钮等）：最大轮廓半径超锚点截面一半 → 轮廓半径等比缩放（y 不动）；
    #    高度超锚点高 2 倍 → 高度方向等比缩放（细高件超高兜底）
    for lt in spec.lathes:
        if lt is anchor or not lt.points:
            continue
        max_r = max(p[0] for p in lt.points)
        changed = False
        if max_r > 0 and max_r > a_sect * 0.5:
            scale = (a_sect * 0.5) / max_r
            log.info(f"3D 零部件钳制 lathe 轮廓半径 ×{scale:.3f}（锚点截面 {a_sect:.2f}）")
            lt.points = [[round(r * scale, 4), y] for r, y in lt.points]
            changed = True
        ys = [p[1] for p in lt.points]
        hgt = max(ys) - min(ys)
        if hgt > ah * 2 and hgt > 0:
            k = (ah * 2) / hgt
            log.info(f"3D 零部件钳制 lathe 高度 ×{k:.3f}（{hgt:.3f} → {ah * 2:.3f}）")
            lt.points = [[r, round(y * k, 4)] for r, y in lt.points]
            changed = True
        if changed:
            fixed += 1
    return fixed


# ===== RAG 先验（RAG-04）：物品名 → 类别先验 → 注入与后校验 =====
# 全部环节失败仅 log.info 跳过（「锦上添花，失败不阻断」），spec=None 回退预设模型库路径不受影响。

# 贴图文本中已含的材质关键词：materials 有明确材质语义时保持原样（保守，改得越少越好）
_MATERIAL_KEYS = ("纸", "塑料", "金属", "玻璃", "木", "竹", "PET", "HDPE", "PP", "PE", "铝", "铁", "不锈钢")


# 先验消费降级路径：RAG-02 就绪走 keyword_search→category_prior；未交付时直接读 priors_3d.json
# （本地名称匹配）。任何失败返回 None，调用方完全现状行为。
def _get_prior(item: str) -> dict | None:
    """按物品名取类别先验（对齐 C2 的 CategoryPrior）：先检索拿命中记录的 sub_category 再取 prior。
    检索为空/异常 → None（不注入，保持现状）。"""
    try:
        if _RAG_OK and retriever is not None:
            hits = retriever.keyword_search(item, top_k=1)
            if hits:
                cat = hits[0].get("sub_category") or hits[0].get("category")
                if cat:
                    prior = retriever.category_prior(cat)
                    if prior:
                        log.info(f"RAG 命中 item={item} category={cat} "
                                 f"prior_count={prior.get('count')} surface={prior.get('suggested_surface')}")
                        return prior
            return None
        # 降级：直接读 rag_data/priors_3d.json + 物品名匹配（RAG-02 未交付）
        if _PRIORS_OK and _match_prior_local is not None:
            prior = _match_prior_local(item)
            if prior:
                log.info(f"RAG 命中(本地先验) item={item} category={prior.get('category')} "
                         f"prior_count={prior.get('count')} surface={prior.get('suggested_surface')}")
                return prior
        return None
    except Exception as e:
        log.info(f"3D 先验检索跳过: {e}")
        return None


def _prior_hint(spec_hint: str) -> str:
    """构造先验注入文本：数据集统计仅作参考，以图片实物为准"""
    return f"【数据集先验参考（按平台回收数据统计，仅作参考，以图片实物为准）】\n{spec_hint}"


def _prior_confirm(spec: Build3dSpec, prior: dict, image_url: str) -> tuple[Build3dSpec, bool]:
    """先验命中 → 第二轮精简确认调用：请 LLM 核对并修正 aspect/材质/表面。
    成功且新 spec 有效则替换；mock/任何失败保留第一轮结果（不阻断）。返回 (spec, replaced)。"""
    try:
        hint = _prior_hint(
            f"已识别物品={spec.item}。数据集先验：常见形态={prior.get('forms')}、"
            f"材质={prior.get('materials')}、建议表面材质={prior.get('suggested_surface')}。"
            "请核对并修正 aspect/材质/表面，仅输出修正后的完整 spec JSON。"
        )
        result = llm_router.invoke(
            capability="visual_verify",
            messages=[
                {"role": "system", "content": _SPEC_PROMPT},
                {"role": "user", "content": [
                    {"type": "image_url", "image_url": {"url": image_url}},
                    {"type": "text", "text": hint},
                ]},
            ],
            response_model=Build3dSpec,
            # 与第一轮同一 scene：mock 供应商按 3d_build 场景返回 Build3dSpec 形状，联调可用
            context={"scene": "3d_build"},
            timeout_seconds=60,
        )
        if result.provider == "mock":
            log.info("3D 先验确认命中 mock，保留第一轮结果（联调占位不适用）")
            return spec, False
        new_spec = result.parsed
        if not (new_spec.primitives or new_spec.lathes or new_spec.tubes):
            return spec, False
        return new_spec, True
    except Exception as e:
        log.info(f"3D 先验确认阶段跳过: {e}")
        return spec, False


def _prior_surface_check(spec: Build3dSpec, prior: dict | None) -> int:
    """先验表面修正（纯规则，不调 LLM）：surface 值为 "none" 且先验建议明确 → 改为建议值。
    仅改 none→建议，禁止改已有具体值；先验缺失/无建议 → 0。返回修正数。"""
    target = (prior or {}).get("suggested_surface")
    if not target:
        return 0
    fixed = 0
    for p in spec.primitives:
        if p.surface == "none":
            p.surface = target
            fixed += 1
    for lt in spec.lathes:
        if lt.surface == "none":
            lt.surface = target
            fixed += 1
    return fixed


def _prior_material_check(spec: Build3dSpec, prior: dict | None) -> int:
    """先验材质兜底（纯规则）：primitive 的贴图文本已含材质关键词时不动作（保持原样）；
    仅当 surface 缺失且先验建议明确时补建议值。返回修正数。"""
    target = (prior or {}).get("suggested_surface")
    if not target:
        return 0
    fixed = 0
    for p in spec.primitives:
        if p.surface != "none":
            continue
        texts = " ".join(
            it.text or "" for fm in (p.materials or []) if fm for it in (fm.items or [])
        )
        if any(k in texts for k in _MATERIAL_KEYS):
            continue
        p.surface = target
        fixed += 1
    return fixed


# ===== 质量分级（Track G）：advanced=spec 级多轮自纠（方案 A，无浏览器）；extreme=+渲染截图对比闭环（方案 B） =====
# 与全服务同一原则：任何环节 mock/异常 → 保留当前 spec 正常返回，绝不阻断。

_CRITIQUE_PROMPT = (
    "你是 3D 建模质量审阅员。输入：真实物品照片 + 程序化 3D 模型的 spec 摘要（几何体/贴图文字清单）。"
    "任务：对照照片逐项检查 spec，挑出与实物不符之处，输出 JSON："
    '{"passed":true/false,"issues":[{"target":"定位路径（如 aspect / primitives[0].materials[4] / '
    'lathes[0].points）","problem":"与实物不符之处","suggestion":"修正建议",'
    '"severity":"critical|minor"}],"fidelity":null}。'
    "【检查清单】"
    "① 比例：aspect 宽高深是否符合实物（如牛奶盒瘦高不可矮胖）；"
    "② 结构：实物有的部件（封口/瓶盖/提手/吸管/折翼等）必须存在，实物没有的部件禁止虚构；"
    "零部件尺寸是否失调（瓶盖/吸管等附加件是否过大——瓶盖高应≈主体高 5%-15%、"
    "吸管长应≈主体高 1.2-1.5 倍且半径 ≤0.05，任何零件不得大于主体）；"
    "③ 材质：透明玻璃/塑料必须 surface=glass + transparent=true + 实物色，"
    "禁止黑色实心块；opacity 不得低于 0.9；纸盒=paper、金属=metal、塑料=plastic；"
    "④ 贴图：主视觉（品牌 logo/产品名/主色块）必须在正面[4]，营养/配料/条码在侧面[0]/[1]；"
    "布局不可上下颠倒（logo 在上 y 小、净含量在下 y 大）；"
    "⑤ 文字：品牌名/产品名/净含量逐字完整，禁止漏字缩写；"
    "⑥ 颜色：主色调取自实物，禁止臆造撞色。"
    "【判定】影响辨认度的问题（比例错误/主视觉缺失或贴错面/透明件建成实心或深色/结构缺件或零件过大）"
    "=critical；色差/局部布局小偏差=minor。无 critical 则 passed=true。issues 最多 8 条，按严重度降序。"
    "若输入附渲染截图（第 1 张=实物原图，其后=spec 渲染的正面/斜视/顶部截图）："
    "以截图为准对比几何与颜色，并额外输出 fidelity（0-1 整体还原度）。"
)

# extreme 渲染对比收敛阈值：fidelity ≥ 0.85 且无 critical 即停
_EXTREME_FIDELITY_PASS = 0.85

# 修正调用 prompt：复用 _SPEC_PROMPT 全部建模规范 + 修正任务指令（输出完整 spec，可独立渲染）
_FIX_PROMPT = (
    _SPEC_PROMPT +
    "【修正任务】上一轮构建的 spec 经审阅存在以下问题，请对照原图逐条修正，"
    "输出修正后的完整 spec JSON（结构与完整度与正常构建相同，可独立渲染，禁止只输出差异片段）。"
    "已点名的问题必须解决；未被点名但原本正确的内容保持不变；"
    "不得引入图片中不存在的新结构部件；修正后仍需通过建模规范自检。"
)


def _spec_digest(spec: Build3dSpec) -> str:
    """spec 精简摘要（审阅/修正调用控 token）：item/aspect/几何体尺寸颜色/各面文字清单。"""
    parts = [f"item={spec.item}", f"aspect={spec.aspect}", f"tick={spec.tick}"]
    for i, p in enumerate(spec.primitives):
        dims = p.size if p.size else ([p.radius, p.height] if p.radius is not None else [])
        parts.append(
            f"primitives[{i}]={p.type} dims={dims} pos={p.pos} color={p.color} "
            f"surface={p.surface} transparent={p.transparent}"
        )
        for fi, fm in enumerate(p.materials or []):
            if fm and fm.items:
                texts = "|".join(it.text or f"[{it.type}]" for it in fm.items)
                parts.append(f"  面[{fi}]=({texts}; 底色:{fm.bg})")
    for i, lt in enumerate(spec.lathes):
        parts.append(
            f"lathes[{i}]=points={lt.points} color={lt.color} surface={lt.surface} "
            f"transparent={lt.transparent}"
        )
    for i, t in enumerate(spec.tubes):
        ends = f"{t.curve[0]}→{t.curve[-1]}" if t.curve else "无曲线"
        parts.append(f"tubes[{i}]=radius={t.radius} color={t.color} 端点={ends}")
    for i, d in enumerate(spec.decals):
        parts.append(f"decals[{i}]={d.shape} text={d.text} color={d.color} pos={d.pos}")
    return "\n".join(parts)


# 玻璃/透明材质钳制：允许半透明（0.3-1.0），前端使用 transmission 实现真实玻璃效果
# opacity 过低（<0.3）会导致渲染不可见，因此设下限 0.3；上限 1.0 允许完全不透明
_GLASS_OPACITY_MIN = 0.3
_TRANSPARENT_OPACITY_MIN = 0.3

# 透明材质颜色校验（防「非法色值渲染成黑块」）：不再做浅色化提亮（用户实测：
# 浅色+低 opacity 会渲染成全透明看不见，深色实色反而可辨识），仅修复非法色值。
_GLASS_FALLBACK_COLOR = "#e8f4f4"  # 无色玻璃参考色（非法色值时兜底）
_TRANSPARENT_FALLBACK_COLOR = "#d8f0e8"  # 透明塑料参考色（非法色值时兜底）


def _hex_to_rgb(color: str | None) -> tuple[int, int, int] | None:
    """'#rrggbb'/'rrggbb' → (r,g,b)；非法（长度错/非 hex/空）返回 None"""
    s = (color or "").strip().lstrip("#")
    if len(s) != 6 or not all(c in "0123456789abcdefABCDEF" for c in s):
        return None
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def _rgb_to_hex(r: int, g: int, b: int) -> str:
    return f"#{max(0, min(255, r)):02x}{max(0, min(255, g)):02x}{max(0, min(255, b)):02x}"


def _rgb_to_hsv(r: int, g: int, b: int) -> tuple[float, float, float]:
    """RGB [0,255] → HSV（H°[0,360), S[0,1], V[0,1]）"""
    rn, gn, bn = r / 255.0, g / 255.0, b / 255.0
    mx = max(rn, gn, bn)
    mn = min(rn, gn, bn)
    df = mx - mn
    if df == 0:
        h = 0.0
    elif mx == rn:
        h = (60 * ((gn - bn) / df) + 360) % 360
    elif mx == gn:
        h = (60 * ((bn - rn) / df) + 120) % 360
    else:
        h = (60 * ((rn - gn) / df) + 240) % 360
    s = 0.0 if mx == 0 else df / mx
    return (h, s, mx)


def _hsv_to_rgb(h: float, s: float, v: float) -> tuple[int, int, int]:
    """HSV → RGB，返回 0-255 整数。"""
    c = v * s
    x = c * (1 - abs((h / 60) % 2 - 1))
    m = v - c
    if h < 60:
        r1, g1, b1 = c, x, 0.0
    elif h < 120:
        r1, g1, b1 = x, c, 0.0
    elif h < 180:
        r1, g1, b1 = 0.0, c, x
    elif h < 240:
        r1, g1, b1 = 0.0, x, c
    elif h < 300:
        r1, g1, b1 = x, 0.0, c
    else:
        r1, g1, b1 = c, 0.0, x
    return (
        int(round((r1 + m) * 255)),
        int(round((g1 + m) * 255)),
        int(round((b1 + m) * 255)),
    )


def _is_transparent_bg(bg: str | None) -> bool:
    """判断 label.bg 是否等价于透明：空、transparent、none，或 #RRGGBBAA/#RGBA 的 alpha=0。"""
    s = (bg or "").strip().lower()
    if not s or s in ("transparent", "none"):
        return True
    hex_part = s.lstrip("#")
    if len(hex_part) == 8:
        try:
            return int(hex_part[6:8], 16) == 0
        except ValueError:
            return False
    if len(hex_part) == 4:
        try:
            return int(hex_part[3], 16) == 0
        except ValueError:
            return False
    return False


def _pick_label_bg_for_bottle(item_name: str, bottle_color: str | None = None) -> str:
    """透明 PET 瓶标签背景兜底：根据物品名猜测标签主色。"""
    name = (item_name or "").lower()
    if any(k in name for k in ("可乐", "汽水", "碳酸")):
        return "#b71c1c"
    if any(k in name for k in ("绿茶", "清茶", "茉莉", "茶饮料")):
        return "#2e7d32"
    if any(k in name for k in ("红茶", "奶茶", "乌龙茶")):
        return "#8d6e63"
    if any(k in name for k in ("橙汁", "果汁", "橙", "芒", "葡萄柚", "柠檬")):
        return "#ef6c00"
    if any(k in name for k in ("牛奶", "酸奶", "豆奶", "乳酸菌", "奶昔")):
        return "#f5f5f5"
    if any(k in name for k in ("水", "矿泉水", "天然水", "纯净水", "饮用水", "苏打水", "泉")):
        return "#1565c0"
    rgb = _hex_to_rgb(bottle_color)
    if rgb is None:
        return "#4a6fa5"
    h, s, v = _rgb_to_hsv(*rgb)
    if s < 0.15:
        return "#4a6fa5"
    bg_v = max(0.25, v * 0.35)
    bg_s = min(1.0, s * 1.15)
    return _rgb_to_hex(*_hsv_to_rgb(h, bg_s, bg_v))


def _lighten_color_preserve_hue(color: str, target_v: float = 0.82, target_s: float | None = None) -> str:
    """提亮颜色同时保留色相：HSV 中提升 V，适度降低 S 避免过艳。
    用于把深蓝/深绿玻璃瓶身提浅为亮蓝/亮绿透明，避免向白色等比混合后发灰。"""
    rgb = _hex_to_rgb(color)
    if rgb is None:
        return "#e8f4f4"
    h, s, v = _rgb_to_hsv(*rgb)
    new_v = max(v, target_v)
    new_s = target_s if target_s is not None else max(0.55, s * 0.85)
    # 极低饱和度（灰/白）不做色相保留，直接提亮
    if s < 0.15:
        new_s = s
    return _rgb_to_hex(*_hsv_to_rgb(h, new_s, new_v))


def _validate_material_colors(spec: Build3dSpec) -> int:
    """透明材质颜色校验（纯规则）：仅修复非法色值（非 #rrggbb → 参考浅色兜底，
    防止前端 toColor 回退中性灰/黑块）。不做浅色化提亮——深色实色（如深绿玻璃）
    比浅色更易辨识；深色由 _clamp_materials 的 opacity 下限保证不透明可见。
    返回修正数。"""
    fixed = 0

    def _fix(obj) -> bool:
        nonlocal fixed
        if obj.surface != "glass" and not obj.transparent:
            return False
        fallback = _GLASS_FALLBACK_COLOR if obj.surface == "glass" else _TRANSPARENT_FALLBACK_COLOR
        if _hex_to_rgb(obj.color) is None:
            log.info(f"3D 透明颜色校验 非法色值 {obj.color!r} → {fallback}（surface={obj.surface}）")
            obj.color = fallback
            fixed += 1
            return True
        return False

    for p in spec.primitives:
        _fix(p)
    for lt in spec.lathes:
        _fix(lt)
    for t in spec.tubes:
        _fix(t)
    return fixed


def _validate_bottle_transparency(spec: Build3dSpec) -> int:
    """PET 瓶透明度校验（纯规则）：检测 LLM 常见错误——将透明 PET 瓶渲染为实心彩色，
    且将标签颜色与瓶身颜色搞反。
    规则：
    1. 检测高饱和度/深色的 lathe（瓶身本体），改为浅色透明
    2. 检测低饱和度灰色/白色的 lathe（应该是标签），如果有其他饱和色存在，
       用那个饱和色替换（修正「蓝瓶身+白标签」→「透明瓶身+蓝标签」的常见错误）
    3. 若瓶身已标记透明但仍是深色（LLM 知道透明却保留深蓝），同样提浅
    返回修正数。"""
    fixed = 0

    # 第一遍：收集所有饱和色（可能是标签颜色），并将需要修正的瓶身标记出来
    saturated_colors = []  # 收集被移除的饱和色
    lathes_to_fix = []

    for lt in spec.lathes:
        rgb = _hex_to_rgb(lt.color)
        if rgb is None:
            continue
        r, g, b = rgb
        max_c = max(r, g, b)
        min_c = min(r, g, b)
        saturation = max_c - min_c
        brightness = (r + g + b) / 3
        is_saturated = saturation > 80 and max_c > 120
        # 情况 A：LLM 把瓶身做成实心饱和色（最常见错误）
        is_opaque = not lt.transparent and (lt.opacity is None or lt.opacity > 0.8)
        # 情况 B：LLM 已标透明/玻璃，但颜色仍是深色 → 软件渲染下透明感丢失，像实心块
        is_dark_glass = (
            lt.surface == "glass"
            and brightness < 180
            and max_c < 220
        )

        if is_saturated and is_opaque:
            # 瓶身本体是饱和色 → 收集颜色并标记为待修复
            saturated_colors.append(lt.color)
            lathes_to_fix.append((lt, False))
        elif is_dark_glass:
            # 透明瓶身颜色太深 → 提浅（亮度提升到 200+）
            lathes_to_fix.append((lt, True))

    # 修复瓶身 → 透明浅色（保留色调，避免向白色等比混合导致发灰）
    for lt, only_lighten in lathes_to_fix:
        # HSV 提亮：提升明度到 0.82，适度降饱和，保留色相
        new_hex = _lighten_color_preserve_hue(lt.color, target_v=0.82, target_s=0.72)
        log.info(f"3D PET 瓶校验 瓶身{'深色' if only_lighten else '饱和色'} {lt.color} → 浅色透明 {new_hex}"
                 f"（surface={lt.surface} transparent={lt.transparent} opacity={lt.opacity}）")
        lt.color = new_hex
        lt.surface = "glass"
        lt.transparent = True
        lt.opacity = max(lt.opacity or 0.45, 0.45)
        fixed += 1

    # 第二遍：检测白色/灰色标签（应该是饱和色）
    if saturated_colors:
        # 取第一个饱和色作为标签颜色
        label_color = saturated_colors[0]
        for lt in spec.lathes:
            rgb = _hex_to_rgb(lt.color)
            if rgb is None:
                continue
            r, g, b = rgb
            max_c = max(r, g, b)
            min_c = min(r, g, b)
            saturation = max_c - min_c
            # 检测低饱和度灰色/白色（应该是标签）
            is_neutral = saturation < 30 and max_c > 200  # 接近白色/灰色
            is_middle = lt.points and len(lt.points) > 0  # 有几何体

            if is_neutral and is_middle:
                log.info(f"3D PET 瓶校验 标签灰色 {lt.color} → 饱和色 {label_color}")
                lt.color = label_color
                lt.surface = "plastic"  # 标签是塑料材质
                lt.transparent = False
                lt.opacity = 1.0
                fixed += 1

    return fixed


def _validate_transparent_consistency(spec: Build3dSpec) -> int:
    """透明语义一致性校验（纯规则，防「透明件建成实心块」）：
    - surface=glass 而 transparent=false → 补 transparent=true（玻璃必透明）；
    - opacity < 0.95 且 transparent=false 且 surface 为 none/plastic → 补 transparent=true
      （LLM 表达透明意图但漏标记，前端按不透明渲染会失真）；
    - 改标记后由 _clamp_materials 把 opacity 钳到下限（0.3），保证可辨识。
    返回修正数。"""
    fixed = 0

    def _fix(obj) -> bool:
        nonlocal fixed
        if obj.surface == "glass" and not obj.transparent:
            log.info(f"3D 透明一致性 glass surface 补 transparent=true（color={obj.color}）")
            obj.transparent = True
            fixed += 1
            return True
        if (not obj.transparent and obj.opacity < 0.95
                and obj.surface in ("none", "plastic")):
            log.info(f"3D 透明一致性 opacity={obj.opacity} 漏标 transparent → 补 true"
                     f"（surface={obj.surface} color={obj.color}）")
            obj.transparent = True
            fixed += 1
            return True
        return False

    for p in spec.primitives:
        _fix(p)
    for lt in spec.lathes:
        _fix(lt)
    for t in spec.tubes:
        _fix(t)
    return fixed


def _clamp_materials(spec: Build3dSpec) -> int:
    """材质钳制（纯规则）：surface=glass → opacity 夹到 [0.3, 1]；
    其余 transparent=true → opacity 夹到 [0.3, 1]。返回修正数。"""
    fixed = 0
    for p in spec.primitives:
        if p.surface == "glass" and p.opacity < _GLASS_OPACITY_MIN:
            p.opacity = _GLASS_OPACITY_MIN
            fixed += 1
        elif p.transparent and p.surface != "glass" and p.opacity < _TRANSPARENT_OPACITY_MIN:
            p.opacity = _TRANSPARENT_OPACITY_MIN
            fixed += 1
    for lt in spec.lathes:
        if lt.surface == "glass" and lt.opacity < _GLASS_OPACITY_MIN:
            lt.opacity = _GLASS_OPACITY_MIN
            fixed += 1
        elif lt.transparent and lt.surface != "glass" and lt.opacity < _TRANSPARENT_OPACITY_MIN:
            lt.opacity = _TRANSPARENT_OPACITY_MIN
            fixed += 1
    for t in spec.tubes:
        if t.surface == "glass" and t.opacity < _GLASS_OPACITY_MIN:
            t.opacity = _GLASS_OPACITY_MIN
            fixed += 1
        elif t.transparent and t.surface != "glass" and t.opacity < _TRANSPARENT_OPACITY_MIN:
            t.opacity = _TRANSPARENT_OPACITY_MIN
            fixed += 1
    return fixed


def _flatten_label_vertical_text(label) -> int:
    """标签竖排文字合并校正（纯规则）：检测 LLM 把标签上每个字单独输出成竖排文字项的
    常见错误（如「天」「然」「水」三个 text 项 x 区间相同、y 递增堆叠），合并为单条横排文字。

    背景：标签画布是横向环绕展开（1024×256，4:1 横向），文字应横排（左→右阅读）。
    LLM 常把每个字单独输出成竖排堆叠项 → 画布上字符纵向排列 → 环绕到瓶身后文字被旋转 90°
    （用户实测「天然水」「TIANRANSHUI」显示为纵向堆叠字符）。

    判定（同时满足才合并）：
    - ≥2 个 text 项，每个 text 去空格后长度 ≤4 字符（短文本：单字/单词/拼音字母）
    - x 区间高度重叠（横向对齐：max(x_start) < min(x_end)，即各项 x 区间有共同交集）
    - y 区间按序排列无显著重叠（纵向堆叠：相邻项 y 重叠 < 区域高度的 50%）
    - 颜色相同或相近（同一文字串通常同色；不同色视为不同元素不合并）
    合并：text 按 y 升序拼接（去空格），region 取首项位置 + 横排宽度（字符数 × 单字宽），
    fontSize/color 取首项，原堆叠项删除，新合并项插入到原首项位置。
    返回合并掉的原始项数（0=无合并）。"""
    items = label.items
    if not items or len(items) < 2:
        return 0

    # 筛选候选竖排文字项：必须是单字/单字母级别（长度 ≤2）且区域接近方形（宽高比 ≤2.0），
    # 这样才不会把已经是横排的多字词（如「天然水」）或角落独立小商标误判为竖排片段。
    text_items = []
    for i, it in enumerate(items):
        if it.type != "text":
            continue
        txt = (it.text or "").strip()
        if not txt or len(txt) > 2:
            continue
        if not it.region or len(it.region) < 4:
            continue
        w, h = it.region[2], it.region[3]
        if w <= 0 or h <= 0 or w / h > 2.0:
            continue
        text_items.append((i, it))
    if len(text_items) < 2:
        return 0

    def _x_overlap(a, b) -> float:
        """x 区间重叠量（>0 表示有重叠）"""
        ax1, ax2 = a.region[0], a.region[0] + a.region[2]
        bx1, bx2 = b.region[0], b.region[0] + b.region[2]
        return min(ax2, bx2) - max(ax1, bx1)

    def _y_overlap_ratio(a, b) -> float:
        """y 区间重叠比例（相对较小项的高度）"""
        ay1, ay2 = a.region[1], a.region[1] + a.region[3]
        by1, by2 = b.region[1], b.region[1] + b.region[3]
        overlap = min(ay2, by2) - max(ay1, by1)
        if overlap <= 0:
            return 0.0
        min_h = min(a.region[3], b.region[3])
        return overlap / min_h if min_h > 0 else 0.0

    def _same_color(a, b) -> bool:
        """颜色相同或相近（忽略大小写差异）"""
        ca = (a.color or "").strip().lower()
        cb = (b.color or "").strip().lower()
        if ca == cb:
            return True
        # 容差：同色系（如 #ffffff 与 #fff 视为相同）
        if ca and cb and ca.lstrip("#") == cb.lstrip("#"):
            return True
        return False

    # 按 y 升序排序，便于检测纵向堆叠
    text_items.sort(key=lambda x: x[1].region[1])

    def _vertical_stack_fit(a, b) -> bool:
        """判断两项是否构成同一竖排文字串中的相邻字符。
        严格限制：相邻项必须 y 方向紧密衔接（gap 小）、高度相近、x 对齐，
        避免把标签上不同位置的两个独立文字块（如品名和角落小商标）误合并。"""
        if not _same_color(a, b):
            return False
        # x 方向必须显著重叠（不是擦肩而过）
        x_overlap = _x_overlap(a, b)
        min_w = min(a.region[2], b.region[2])
        if x_overlap <= 0 or (min_w > 0 and x_overlap < min_w * 0.3):
            return False
        # y 方向无显著重叠（真正的竖排字符是上下排列，不重叠）
        if _y_overlap_ratio(a, b) >= 0.35:
            return False
        # 相邻间隙不能太大：gap < 较小项高度的 0.6 倍，且总体高度相近
        ay1, ay2 = a.region[1], a.region[1] + a.region[3]
        by1, by2 = b.region[1], b.region[1] + b.region[3]
        gap = by1 - ay2 if by1 >= ay2 else ay1 - by2
        min_h = min(a.region[3], b.region[3])
        if gap > min_h * 0.8:
            return False
        h_ratio = max(a.region[3], b.region[3]) / min_h if min_h > 0 else 1.0
        if h_ratio > 2.5:
            return False
        return True

    # 分组：只合并 y 排序后连续满足 stack_fit 的相邻项
    groups: list[list[tuple[int, object]]] = []
    i = 0
    while i < len(text_items):
        group = [text_items[i]]
        j = i + 1
        while j < len(text_items) and _vertical_stack_fit(group[-1][1], text_items[j][1]):
            group.append(text_items[j])
            j += 1
        if len(group) >= 2:
            groups.append(group)
        i = j

    if not groups:
        return 0

    # 构建新 items 列表（保留原顺序，删除堆叠项，插入合并项）
    new_items = list(items)
    total_merged = 0
    # 从后往前处理各组，避免索引偏移
    for group in sorted(groups, key=lambda g: g[0][0], reverse=True):
        # group 已按 y 升序（text_items 排序后保留）
        group.sort(key=lambda x: x[1].region[1])
        merged_text = "".join((it.text or "").strip() for _, it in group)
        if not merged_text:
            continue

        first = group[0][1]
        # 横排化：新 region 宽度 = 字符数 × 单字高度 × 0.55（中文近似正方形，英文窄一些）
        n_chars = len(merged_text)
        char_h = first.region[3]  # 单字高度（原堆叠项每项高度）
        # 横排总宽度，上限 0.95 防溢出画布
        new_w = min(0.95, max(first.region[2] * n_chars, n_chars * char_h * 0.6))
        new_h = char_h  # 单字高度
        # 横排居中：x 取首项中心，向两侧扩展
        first_cx = first.region[0] + first.region[2] / 2
        new_x = first_cx - new_w / 2
        # 边界夹紧
        if new_x < 0.02:
            new_x = 0.02
        if new_x + new_w > 0.98:
            new_x = 0.98 - new_w
        new_y = first.region[1]
        # y 也夹紧到画布内
        if new_y + new_h > 0.98:
            new_y = max(0.02, 0.98 - new_h)

        merged_item = DecalItem(
            type="text",
            text=merged_text,
            color=first.color,
            region=[round(new_x, 4), round(new_y, 4), round(new_w, 4), round(new_h, 4)],
            fontSize=first.fontSize,
            border=False,
        )

        # 删除原堆叠项（按原始 items 索引从大到小删）
        indices_to_remove = sorted([orig_i for orig_i, _ in group], reverse=True)
        for idx in indices_to_remove:
            new_items.pop(idx)
        # 插入合并项到原首项位置
        insert_idx = min(orig_i for orig_i, _ in group)
        new_items.insert(insert_idx, merged_item)
        total_merged += len(group)

    if total_merged > 0:
        label.items = new_items
    return total_merged


def _flatten_all_label_vertical_text(spec: Build3dSpec) -> int:
    """遍历所有 lathe/primitive 的 label，执行竖排文字合并校正。
    返回合并掉的总项数（0=无合并）。"""
    fixed = 0
    for lt in spec.lathes:
        if lt.label is not None:
            fixed += _flatten_label_vertical_text(lt.label)
    for p in spec.primitives:
        if p.label is not None:
            fixed += _flatten_label_vertical_text(p.label)
    return fixed


def _ensure_bottle_label_background(spec: Build3dSpec) -> None:
    """PET 瓶标签背景兜底：若透明玻璃瓶身的标签没有有效背景色，
    给标签补上合适的不透明背景，保证文字对比度。"""
    for lt in spec.lathes:
        if lt.surface != "glass" or not lt.transparent:
            continue
        if not lt.label:
            continue
        if not _is_transparent_bg(lt.label.bg):
            continue
        label_bg = _pick_label_bg_for_bottle(spec.item, lt.color)
        log.info(f"3D PET 瓶标签背景兜底 item={spec.item} {lt.label.bg} → {label_bg}")
        lt.label.bg = label_bg


def _ensure_bottle_glass_surface(spec: Build3dSpec) -> int:
    """透明瓶身 surface 兜底：LLM 常把 PET/玻璃杯身输出成 surface=plastic，
    导致软件渲染下没有透明折射感而呈白色实心。对明显是瓶/杯/饮料的透明 lathe，
    强制改为 surface=glass，由前端走玻璃材质路径。"""
    fixed = 0
    keywords = ("瓶", "杯", "水", "饮料", "矿泉水", "天然水", "纯净水", "玻璃")
    item = (spec.item or "").lower()
    looks_like_bottle = any(k in item for k in keywords)
    for lt in spec.lathes:
        if lt.transparent and lt.surface in ("plastic", "none") and lt.opacity is not None and lt.opacity <= 0.6:
            if looks_like_bottle or lt.label is not None:
                log.info(f"3D 透明瓶身 surface 兜底 {lt.surface} → glass（item={spec.item}）")
                lt.surface = "glass"
                fixed += 1
    return fixed


def _normalize_label_font_sizes(spec: Build3dSpec) -> int:
    """标签文字字号兜底：LLM 常把 fontSize 设得过小（0.1-0.2），
    导致 Canvas 上文字只有几个像素。将过小的字号提到可读范围，
    同时保留较大的字号不变。"""
    fixed = 0

    def _fix_label(label) -> None:
        nonlocal fixed
        for it in (label.items or []):
            if it.type != "text":
                continue
            # 主文字区域（region 高度 > 0.06）字号不能太小，否则 Canvas 上只有几个像素
            region_h = (it.region[3] if it.region and len(it.region) > 3 else 0.2)
            if (it.fontSize is None or it.fontSize < 0.5) and region_h > 0.06:
                it.fontSize = 0.7
                fixed += 1

    for lt in spec.lathes:
        if lt.label is not None:
            _fix_label(lt.label)
    for p in spec.primitives:
        if p.label is not None:
            _fix_label(p.label)
    return fixed


def _normalize_label_layout(label) -> bool:
    """标签纵向布局兜底：LLM 输出的 items region 经常上下重叠
    （如品名 region 与拼音 region 重叠，或文字压在浅色图案上），渲染后互相遮挡。
    按原始上下顺序重新排布所有 items，保证不重叠且整体落在画布内。"""
    items = label.items
    if not items:
        return False
    valid = [it for it in items if it.region and len(it.region) >= 4]
    if len(valid) < 2:
        return False
    # 按原始 y 从上到下排序，保留 LLM 的上下意图
    valid.sort(key=lambda it: it.region[1])
    gap = 0.02
    total = sum(it.region[3] for it in valid) + gap * (len(valid) - 1)
    scale = 1.0 if total <= 0.95 else 0.95 / total
    y = 0.02
    for it in valid:
        h = max(0.04, it.region[3] * scale)
        it.region[1] = round(y, 4)
        it.region[3] = round(h, 4)
        # 文字字号占 region 高度的 0.7，保证居中且清晰
        if it.type == "text":
            it.fontSize = 0.7
        y += h + gap
    label.items = valid
    return True


def _normalize_all_label_layouts(spec: Build3dSpec) -> int:
    """遍历所有 label，执行文字纵向布局校正。"""
    fixed = 0
    for lt in spec.lathes:
        if lt.label is not None and _normalize_label_layout(lt.label):
            fixed += 1
    for p in spec.primitives:
        if p.label is not None and _normalize_label_layout(p.label):
            fixed += 1
    return fixed


def _is_cap_primitive(p: SpecPrimitive) -> bool:
    """启发式判断一个 primitive 是否是瓶盖/瓶口件：
    圆柱体、高度小、位于物体上半部、白色/浅色塑料或金属表面。"""
    if p.type != "cylinder":
        return False
    if p.height is None or p.height > 0.18:
        return False
    if p.radius is None or p.radius > 0.35:
        return False
    if p.pos[1] < 0.35:
        return False
    color = (p.color or "").lower()
    surface = p.surface or "none"
    light_colors = {"#ffffff", "#fff", "#f5f5f5", "#f8f8f8", "#eeeeee", "#fafafa"}
    is_light = color in light_colors
    if not is_light:
        rgb = _hex_to_rgb(p.color)
        if rgb is not None:
            is_light = sum(rgb) / 3 > 200
    return surface in ("plastic", "metal") and is_light


def _ensure_bottle_shape(pts: list[list[float]], target_max_r: float, target_height: float) -> list[list[float]]:
    """保证瓶身轮廓点包含底部→瓶身→肩部→颈部→瓶口的基本曲线，
    同时控制总点数不超过 schema 限制。"""
    pts = sorted(pts, key=lambda p: p[1])
    # 底部：确保从 y=0 开始，且底部略内收（PET 瓶底通常较平，只轻微内收）
    if pts[0][1] > 0.005:
        base_r = max(pts[0][0] * 0.95, target_max_r * 0.78)
        pts.insert(0, [round(base_r, 4), 0.0])
    # 瓶口：确保顶部有收窄的颈部/瓶口
    if pts[-1][1] < target_height - 0.01:
        top_r = pts[-1][0]
        neck_r = max(top_r * 0.82, target_max_r * 0.28)
        mouth_r = max(top_r * 0.88, target_max_r * 0.32)
        pts.append([round(neck_r, 4), round(target_height * 0.94, 4)])
        pts.append([round(mouth_r, 4), round(target_height, 4)])
    return pts


def _add_bottle_ridges(pts: list[list[float]], target_max_r: float) -> list[list[float]]:
    """在 PET 瓶/饮料瓶身中下部加入水平加强筋/凹槽，使轮廓更接近真实瓶体。
    只在有足够空间时插入，且总点数不超过 LatheSpec 上限。"""
    pts = sorted(pts, key=lambda p: p[1])
    if len(pts) < 4:
        return pts

    h = max(p[1] for p in pts)
    if h <= 0:
        return pts

    # 凹槽区域：瓶身中下部 3 道环向凹槽
    ridge_zones = [(0.26 * h, 0.33 * h), (0.38 * h, 0.45 * h), (0.50 * h, 0.57 * h)]
    ridge_depth = max(target_max_r * 0.055, 0.012)

    new_pts = []
    for i in range(len(pts) - 1):
        r1, y1 = pts[i]
        r2, y2 = pts[i + 1]
        new_pts.append([r1, y1])

        for zone_low, zone_high in ridge_zones:
            if y1 < zone_low and y2 > zone_high:
                cy = (zone_low + zone_high) / 2
                t = (cy - y1) / (y2 - y1) if abs(y2 - y1) > 1e-6 else 0
                base_r = r1 + (r2 - r1) * t
                groove_r = max(base_r - ridge_depth, target_max_r * 0.72)
                # 每个凹槽：下降→回升两个点，保持平滑
                new_pts.append([round(groove_r, 4), round(cy - 0.012, 4)])
                new_pts.append([round(base_r, 4), round(cy + 0.012, 4)])

    new_pts.append(pts[-1])

    # 按 y 排序并去重
    new_pts = sorted(new_pts, key=lambda p: p[1])
    deduped: list[list[float]] = []
    for p in new_pts:
        if not deduped or abs(p[1] - deduped[-1][1]) > 0.001:
            deduped.append(p)

    # 点数上限保护（LatheSpec max_length=24）：均匀抽稀
    if len(deduped) > 24:
        step = (len(deduped) - 1) / 23
        deduped = [deduped[round(i * step)] for i in range(24)]
    return deduped


def _improve_bottle_label_stripe(label: SideLabel | None) -> bool:
    """优化瓶身标签顶部的图案条：如果存在顶部 stripe（通常表示天空/雪山），
    将其横向铺满整圈，避免标签环绕时出现两侧空白。"""
    if not label or not label.items:
        return False
    changed = False
    for it in label.items:
        if it.type == "stripe" and it.region and len(it.region) >= 4:
            ry, rh = it.region[1], it.region[3]
            # 顶部图案条：y<0.15 且高度 <0.35
            if ry < 0.15 and rh < 0.35:
                if it.region[0] > 0.02 or it.region[0] + it.region[2] < 0.98:
                    it.region[0] = 0.0
                    it.region[2] = 1.0
                    changed = True
    return changed


def _normalize_bottle_geometry(spec: Build3dSpec) -> int:
    """修正 LLM 对瓶/罐/杯类旋转体的常见空间错误：
    1. 瓶身 lathe 过矮（LLM 常输出高仅 0.2 的矮胖瓶，却将瓶盖放在 y≈1.0），
       导致瓶盖与瓶身分离、整体被压扁。
    2. 瓶盖圆柱被错误赋予六面印刷内容（营养表/配料表等）。
    3. 瓶身轮廓缺少底部/肩部/颈部曲线，以及 PET 瓶常见的水平加强筋。
    4. 标签顶部图案条未铺满整圈。
    只在检测到明显异常时修正，避免误伤已正确的 spec。
    返回修正的几何体数。
    """
    item = (spec.item or "").lower()
    bottle_keywords = (
        "瓶", "水", "饮料", "矿泉水", "天然水", "纯净水", "饮用水",
        "苏打水", "可乐", "汽水", "碳酸", "果汁", "茶", "牛奶", "酸奶", "奶昔", "杯",
    )
    if not any(k in item for k in bottle_keywords):
        return 0

    # 找主瓶身 lathe：优先透明玻璃/塑料带标签的旋转体
    main_bottle = None
    for lt in spec.lathes:
        if (lt.surface == "glass" or lt.transparent) and lt.label is not None:
            main_bottle = lt
            break
    if main_bottle is None:
        for lt in spec.lathes:
            if lt.surface == "glass" or lt.transparent:
                main_bottle = lt
                break
    if main_bottle is None:
        return 0

    pts = main_bottle.points
    if not pts or len(pts) < 2:
        return 0

    ys = [p[1] for p in pts]
    rs = [p[0] for p in pts]
    current_bottom = min(ys)
    current_top = max(ys)
    current_height = current_top - current_bottom
    current_max_r = max(rs)

    ref_h = spec.aspect[1] if spec.aspect and spec.aspect[1] > 0 else 1.0
    target_body_h = ref_h * 0.86  # 瓶身占总体 86%，剩余给瓶盖
    target_width = spec.aspect[0] if spec.aspect and spec.aspect[0] > 0 else 0.55
    target_max_r = target_width / 2.0

    fixed = 0

    # 触发条件：瓶身明显过矮，或半径/高度比不自然（过于矮胖）
    aspect_ratio = current_max_r / max(current_height, 1e-6)
    needs_scale = current_height < target_body_h * 0.55 or aspect_ratio > 0.85

    if needs_scale and current_height > 0 and current_max_r > 0:
        scale_y = target_body_h / current_height
        # 半径按目标 aspect 校准，但不过度放大（上限 1.4 倍防走形）
        scale_r = min(target_max_r / current_max_r, 1.4)
        new_pts = []
        for r, y in pts:
            new_y = (y - current_bottom) * scale_y
            new_r = r * scale_r
            new_pts.append([round(new_r, 4), round(new_y, 4)])
        # 补全底部与瓶口特征点
        new_pts = _ensure_bottle_shape(new_pts, target_max_r, target_body_h)
        # 加入水平加强筋/凹槽
        new_pts = _add_bottle_ridges(new_pts, target_max_r)
        main_bottle.points = new_pts
        fixed += 1
        log.info(f"3D 瓶身几何校准 item={spec.item} height {current_height:.3f}→{target_body_h:.3f}, "
                 f"max_r {current_max_r:.3f}→{target_max_r:.3f}")

    # 重新计算瓶口参数
    pts = main_bottle.points
    ys = [p[1] for p in pts]
    rs = [p[0] for p in pts]
    bottle_top_y = max(ys)
    bottle_top_r = max(p[0] for p in pts if p[1] >= bottle_top_y - 0.05)

    # 修正瓶盖
    for p in spec.primitives:
        if _is_cap_primitive(p):
            # 瓶盖不应有六面贴图（营养表/配料等），清空
            if p.materials:
                p.materials = None
                fixed += 1
            cap_h = p.height if p.height is not None else 0.1
            expected_y = bottle_top_y + cap_h / 2
            if abs(p.pos[1] - expected_y) > 0.05:
                p.pos[1] = round(expected_y, 4)
                fixed += 1
            desired_r = min(max(bottle_top_r * 1.35, bottle_top_r + 0.02), target_max_r * 1.08)
            if p.radius is not None and abs(p.radius - desired_r) > 0.03:
                p.radius = round(desired_r, 4)
                fixed += 1

    # 优化标签顶部图案条铺满
    if main_bottle.label is not None and _improve_bottle_label_stripe(main_bottle.label):
        fixed += 1

    return fixed


def _snap_floating_primitives(spec: Build3dSpec) -> int:
    """通用兜底：如果某个 primitive（通常是瓶盖/附件）悬浮在主体上方很远，
    将其下落到主体顶面。只对明显异常（间距 >0.3）生效，避免误伤正常结构。
    与 _normalize_bottle_geometry 互补：后者专攻瓶罐旋转体，本函数覆盖其他类物体。"""
    anchor = _max_anchor(spec)
    if anchor is None:
        return 0

    # 计算锚点顶面 y
    if hasattr(anchor, "points") and anchor.points:
        py = anchor.pos[1] if anchor.pos else 0.0
        top_y = py + max(p[1] for p in anchor.points)
    elif hasattr(anchor, "size") and anchor.size:
        py = anchor.pos[1] if anchor.pos else 0.0
        top_y = py + anchor.size[1] / 2
    elif hasattr(anchor, "radius") and anchor.radius is not None and anchor.height is not None:
        py = anchor.pos[1] if anchor.pos else 0.0
        top_y = py + anchor.height / 2
    else:
        return 0

    fixed = 0
    for p in spec.primitives:
        if p is anchor:
            continue
        p_height = p.height if p.height is not None else (
            p.size[1] if p.size and len(p.size) > 1 else (
                p.radius * 2 if p.radius is not None else 0.1
            )
        )
        p_bottom = p.pos[1] - p_height / 2
        # 只处理明显悬浮在主体上方的附件
        if p_bottom > top_y + 0.3:
            expected_y = top_y + p_height / 2 + 0.01
            log.info(f"3D 悬浮件吸附 {p.type} y={p.pos[1]:.3f}→{expected_y:.3f}（主体顶面 {top_y:.3f}）")
            p.pos[1] = round(expected_y, 4)
            fixed += 1
    return fixed


def _clamp_label_text_widths(spec: Build3dSpec) -> int:
    """标签文字区域宽度兜底：LLM 常把主文字宽度设为 0.8~0.9（几乎绕瓶一圈），
    而 front 视角只能看到约 40% 的瓶身周长，导致文字大部分绕到侧面/背面。
    将过宽文字区域居中压缩到 0.38 以内，确保主文字落在正面可视弧内；
    保留角落小标识（宽度 <0.3 或 x<0.1）不变。"""
    fixed = 0
    max_w = 0.38

    def _fix_label(label) -> None:
        nonlocal fixed
        for it in (label.items or []):
            if it.type != "text" or not it.region or len(it.region) < 4:
                continue
            x, y, w, h = it.region
            if w > max_w:
                # 小角落标识不处理：x 靠左且宽度小
                if x < 0.1 and w < 0.35:
                    continue
                new_w = max_w
                new_x = 0.5 - new_w / 2
                it.region = [round(new_x, 4), round(y, 4), round(new_w, 4), round(h, 4)]
                fixed += 1

    for lt in spec.lathes:
        if lt.label is not None:
            _fix_label(lt.label)
    for p in spec.primitives:
        if p.label is not None:
            _fix_label(p.label)
    return fixed


def _polish_transparent_surfaces(spec: Build3dSpec) -> int:
    """透明材质光泽兜底：LLM 常给透明 PET 瓶/玻璃杯设置 roughness=0.5+，
    软件渲染下显得发白、浑浊。对薄壁透明件降低粗糙度，使其更接近真实清澈感。"""
    fixed = 0
    for lt in spec.lathes:
        if lt.transparent and lt.opacity is not None and lt.opacity <= 0.6:
            if lt.roughness is None or lt.roughness > 0.15:
                lt.roughness = 0.08
                fixed += 1
    for p in spec.primitives:
        if p.transparent and p.opacity is not None and p.opacity <= 0.6:
            if p.roughness is None or p.roughness > 0.15:
                p.roughness = 0.08
                fixed += 1
    return fixed


def _light_postprocess(spec: Build3dSpec) -> None:
    """修正轮替换 spec 后重跑的轻量规则后处理（面归一化/坐标翻转/插入件吸附）。
    与主流程共用同一批规则函数；补全阶段与先验确认不在此重跑（控时延）。"""
    _validate_bottle_transparency(spec)
    _validate_transparent_consistency(spec)
    _clamp_materials(spec)
    _validate_material_colors(spec)
    _flatten_all_label_vertical_text(spec)
    _ensure_bottle_label_background(spec)
    _ensure_bottle_glass_surface(spec)
    _normalize_label_font_sizes(spec)
    _normalize_all_label_layouts(spec)
    _clamp_label_text_widths(spec)
    _polish_transparent_surfaces(spec)
    _normalize_bottle_geometry(spec)
    _snap_floating_primitives(spec)
    for p in spec.primitives:
        if p.materials:
            fixed = _normalize_materials(p.materials)
            if fixed is not p.materials:
                p.materials = fixed
        for fm in (p.materials or []):
            if fm is not None:
                _auto_flip_face(fm)
    for lt in spec.lathes:
        if lt.label is not None:
            _auto_flip_face(lt.label)
    anchor = _max_anchor(spec)
    if anchor is not None:
        _clamp_attachments(spec, anchor)  # 零部件尺寸钳制（吸管/把手等过大兜底）
        _snap_tubes(spec, anchor)


def _fix_call(spec: Build3dSpec, image_url: str, issues: list[SpecIssue]) -> Build3dSpec | None:
    """修正调用：原图 + 当前完整 spec + 审阅问题 → 输出修正后完整 Build3dSpec。
    mock/异常/无几何体 → None（保留原 spec，不阻断）。"""
    issue_text = "\n".join(
        f"- [{i.severity}] {i.target}: {i.problem}（建议：{i.suggestion}）" for i in issues
    )
    try:
        result = llm_router.invoke(
            capability="visual_verify",
            messages=[
                {"role": "system", "content": _FIX_PROMPT},
                {"role": "user", "content": [
                    {"type": "image_url", "image_url": {"url": image_url}},
                    {"type": "text", "text": (
                        f"当前 spec（完整 JSON）：\n{spec.model_dump_json()}\n\n"
                        f"审阅发现的问题（按定位路径逐条修正）：\n{issue_text}"
                    )},
                ]},
            ],
            response_model=Build3dSpec,
            # 与第一轮同一 scene：mock 供应商按 3d_build 场景返回 Build3dSpec 形状，联调可用
            context={"scene": "3d_build"},
            timeout_seconds=60,
        )
        if result.provider == "mock":
            log.info("3D 修正调用命中 mock，保留当前 spec（联调占位不适用）")
            return None
        new_spec = result.parsed
        if not (new_spec.primitives or new_spec.lathes or new_spec.tubes):
            log.info("3D 修正调用产出空 spec，保留当前 spec")
            return None
        return new_spec
    except Exception as e:  # 修正是锦上添花：任何失败都不阻断
        log.info(f"3D 修正调用跳过: {e}")
        return None


def _advanced_refine(spec: Build3dSpec, image_url: str) -> tuple[Build3dSpec, str]:
    """advanced 自纠循环（方案 A，无浏览器）：审阅 → 修正，≤2 轮。

    早停：passed=true 或 无 critical；防震荡：issues 的 target 集合与上一轮完全相同 → 停。
    每轮替换后重跑轻量后处理（_light_postprocess：归一化/翻转/吸附），不重跑补全与先验确认。
    任何调用 mock/异常 → 保留当前 spec 正常返回（失败不阻断）。
    返回 (spec, 实际模式)。"""
    current = spec
    prev_targets: set[str] | None = None
    for round_no in (1, 2):
        critique = _critique_call(current, image_url)
        if critique is None:
            log.info(f"3D 高级模式 round={round_no} 审阅不可用，保留当前 spec")
            break
        critical = [i for i in critique.issues if i.severity == "critical"]
        if critique.passed or not critical:
            # 早停：无 critical（minor 可接受，不为此消耗修正轮）
            log.info(f"3D 高级模式 round={round_no} 早停（无 critical）"
                     f" issues={len(critique.issues)} critical=0 替换=false")
            break
        targets = {i.target for i in critique.issues}
        if prev_targets is not None and targets == prev_targets:
            # 防震荡：同一批问题改不动，停止避免振荡（借 skill 的 oscillation stop）
            log.info(f"3D 高级模式 round={round_no} 防震荡停止（issues.target 与上轮相同）"
                     f" issues={len(critique.issues)} critical={len(critical)} 替换=false")
            break
        prev_targets = targets
        fixed = _fix_call(current, image_url, critique.issues)
        if fixed is None:
            break
        _light_postprocess(fixed)  # 修正轮后处理：不重跑补全/先验确认（契约 C4-8）
        current = fixed
        log.info(f"3D 高级模式 round={round_no} issues={len(critique.issues)} "
                 f"critical={len(critical)} 替换=true")
    return current, "advanced"


def _critique_call(spec: Build3dSpec, image_url: str,
                   shots: list[str] | None = None) -> SpecCritique | None:
    """审阅调用：原图（+可选渲染截图）对照 spec 摘要挑错。mock/异常返回 None（不阻断）。"""
    content: list = [{"type": "image_url", "image_url": {"url": image_url}}]
    for s in shots or []:
        content.append({"type": "image_url", "image_url": {"url": s}})
    text = f"spec 摘要：\n{_spec_digest(spec)}"
    if shots:
        text += "\n（共 {} 张附图：第 1 张=实物原图，第 2-{} 张=spec 渲染截图 正面/斜视/顶部）".format(
            len(shots) + 1, len(shots) + 1)
    try:
        result = llm_router.invoke(
            capability="visual_verify",
            messages=[
                {"role": "system", "content": _CRITIQUE_PROMPT},
                {"role": "user", "content": content + [
                    {"type": "text", "text": text},
                ]},
            ],
            response_model=SpecCritique,
            context={"scene": "3d_critique"},
            timeout_seconds=45,
        )
        if result.provider == "mock":
            log.info("3D 审阅命中 mock，跳过（联调占位不适用）")
            return None
        return result.parsed
    except Exception as e:  # 审阅是锦上添花：任何失败都不阻断
        log.info(f"3D 审阅调用跳过: {e}")
        return None


# extreme 挂载点（契约 C4-5）：render3d 真实闭环；不可用时 build3d 按 advanced 降级，不改分支逻辑。
# 函数签名冻结：_extreme_refine(spec, image_url) -> tuple[Build3dSpec, str]（(spec, 实际模式)）。
_extreme_refine = None
try:
    from app.services.render3d import extreme_refine as _extreme_refine_impl
    _extreme_refine = _extreme_refine_impl
    log.info("3D extreme 渲染闭环已挂载（render3d.extreme_refine）")
except Exception as e:  # playwright 未安装/导入失败：保持占位，extreme 自动降级 advanced
    log.info(f"3D extreme 渲染闭环不可用（extreme 降级 advanced）: {e}")


def build3d(image_url: str, mode: str = "standard") -> Build3dResponse:
    """3D 构建入口。mode 为 Track G 契约参数：standard=现状单链路（先验注入+后校验），
    advanced=+spec 级多轮自纠（≤2 轮），extreme=+渲染截图对比闭环（B4 挂载点，未就绪时降级 advanced）。
    签名兼容 verify 路由，standard 语义不受影响。"""
    start = time.perf_counter()
    effective_mode = mode

    def _fail(error: str, model_name: str = "", degraded: bool = False) -> Build3dResponse:
        log.warning(f"3D 构建失败（回退预设模型库）: {error}")
        return Build3dResponse(
            spec=None,
            model_name=model_name,
            degraded=degraded,
            error=error,
            elapsed_ms=int((time.perf_counter() - start) * 1000),
            mode=effective_mode,
        )

    try:
        result = llm_router.invoke(
            capability="visual_verify",
            messages=[
                {"role": "system", "content": _SPEC_PROMPT},
                {"role": "user", "content": [
                    {"type": "image_url", "image_url": {"url": image_url}},
                ]},
            ],
            response_model=Build3dSpec,
            context={"scene": "3d_build"},
            # spec 长输出（5-12 几何体 + 推理模型思维链）显著慢于识别：
            # 覆盖供应商默认 15s，避免生成到一半被读超时掐断
            timeout_seconds=60,
        )
    except Exception as e:  # 路由链耗尽 / LLM 异常等：不阻断
        return _fail(f"LLM 调用失败: {e}")

    spec: Build3dSpec = result.parsed
    if not (spec.primitives or spec.lathes or spec.tubes):
        return _fail("LLM 未识别出可构建的物品（图片无效或非物品）",
                     f"{result.provider}/{result.model}", result.degraded)

    # ===== RAG 先验（RAG-04）：物品名 → 类别先验 → 第二轮精简确认 =====
    # 第一轮调用前无法预知物品名 → 不注入（保持现状）；拿到 item 后才可能触发。
    # 第二轮仅当 prior 存在且 RAG-OK 时触发；无 prior / 检索失败 → 完全现状行为。
    prior: dict | None = None
    if _PRIOR_ENABLED:
        prior = _get_prior(spec.item)
        if prior and _RAG_OK:
            new_spec, replaced = _prior_confirm(spec, prior, image_url)
            if replaced:
                log.info(f"3D 先验确认替换 spec item={spec.item} category={prior.get('category')} "
                         f"model={result.provider}/{result.model}")
                spec = new_spec

    # 面索引归一化：LLM 可能把主视觉写在错误索引（压缩数组/写错位），渲染器按索引贴面会贴错面；
    # 按内容语义重排到六面索引（主视觉→[4] 正面，营养/条码→[0]/[1]）
    normalized = 0
    for p in spec.primitives:
        if p.materials:
            fixed = _normalize_materials(p.materials)
            if fixed is not p.materials:
                p.materials = fixed
                normalized += 1
    if normalized:
        log.info(f"3D spec 面索引归一化 {normalized} 个 box（按内容语义重排六面）")

    # PET 瓶透明度校验：检测饱和蓝色瓶子（LLM 常见错误），改为浅蓝透明材质
    bottle_fixed = _validate_bottle_transparency(spec)
    if bottle_fixed:
        log.info(f"3D spec PET 瓶透明度校验修正 {bottle_fixed} 处（饱和蓝色→浅蓝透明）")

    # 玻璃/透明材质钳制：LLM 输出的 opacity 可能过低（<0.3），需要钳到下限保证可辨识。
    # 顺序：先补透明一致性标记（glass→transparent、漏标低 opacity 补标），再钳 opacity 下限，
    # 再校验颜色（深色/非法色 → 浅色）。保证新补的透明件也吃到 opacity 钳制。
    consistency_fixed = _validate_transparent_consistency(spec)
    if consistency_fixed:
        log.info(f"3D spec 透明一致性校验修正 {consistency_fixed} 处（补 transparent 标记）")
    clamped = _clamp_materials(spec)
    if clamped:
        log.info(f"3D spec 玻璃/透明材质钳制 {clamped} 处（opacity 下限保护）")
    color_fixed = _validate_material_colors(spec)
    if color_fixed:
        log.info(f"3D spec 透明颜色校验修正 {color_fixed} 处（深色/非法色提亮为浅色）")

    # 布局方向自动校正：LLM 常按数学坐标系（y 向上）输出 region，渲染器是左上原点（y 向下）
    flipped = 0
    for p in spec.primitives:
        for fm in (p.materials or []):
            if fm is not None and _auto_flip_face(fm):
                flipped += 1
    for lt in spec.lathes:
        if lt.label is not None and _auto_flip_face(lt.label):
            flipped += 1
    if flipped:
        log.info(f"3D spec 布局方向自动校正 {flipped} 个面（LLM 数学坐标系 → 渲染器左上原点）")

    # 标签竖排文字合并：LLM 常把标签上每个字单独输出成竖排堆叠项（x 相同 y 递增），
    # 画布上字符纵向排列 → 环绕到瓶身后文字被旋转 90°（用户实测「天然水」显示为纵向堆叠）。
    # 检测并合并为单条横排文字，保证标签文字横向阅读。
    label_flattened = _flatten_all_label_vertical_text(spec)
    if label_flattened:
        log.info(f"3D spec 标签竖排文字合并 {label_flattened} 项（纵向堆叠 → 横排，修复文字旋转失真）")

    # PET 瓶标签背景兜底：LLM 有时把 label.bg 输出为透明/none/#00000000，导致白色文字直接印在透明瓶身上
    # 对比度不足；若瓶身是透明玻璃且标签无有效背景，则给标签补上合适的不透明背景。
    _ensure_bottle_label_background(spec)
    glass_surface_fixed = _ensure_bottle_glass_surface(spec)
    if glass_surface_fixed:
        log.info(f"3D 透明瓶身 surface 兜底 {glass_surface_fixed} 处（plastic/none → glass）")
    label_font_fixed = _normalize_label_font_sizes(spec)
    if label_font_fixed:
        log.info(f"3D spec 标签字号兜底 {label_font_fixed} 项（过小学号 → 可读范围）")
    label_layout_fixed = _normalize_all_label_layouts(spec)
    if label_layout_fixed:
        log.info(f"3D spec 标签布局兜底 {label_layout_fixed} 个 label（文字纵向堆叠 → 自动排列）")

    # 瓶/罐/杯类几何体全局校准：修正 LLM 常见的瓶身过矮、瓶盖悬浮、瓶盖贴图错误等问题
    bottle_geo_fixed = _normalize_bottle_geometry(spec)
    if bottle_geo_fixed:
        log.info(f"3D 瓶罐几何校准 {bottle_geo_fixed} 处（瓶身/瓶盖位置与比例）")

    # 通用悬浮件吸附：非瓶罐类物体若出现附件（如瓶盖/装饰件）远离主体，落到主体顶面
    floating_fixed = _snap_floating_primitives(spec)
    if floating_fixed:
        log.info(f"3D 通用悬浮件吸附 {floating_fixed} 处")

    # 缺失面补全：图片只拍到一面/两面时，不可见面按包装行业规范补全（锦上添花，失败不阻断）
    found = _missing_faces(spec)
    anchor = None
    if found:
        box, missing = found
        anchor = box
        completed = _complete_missing_faces(spec, box, missing)
        if completed:
            log.info(f"3D 缺失面补全完成 item={spec.item} 补全面={missing[:completed]} "
                     f"model={result.provider}/{result.model}")
    # 吸管等插入件贴面拼接：LLM 常把 tube 曲线算飘（悬浮/脱离主体），按主体表面吸附。
    # 锚点独立寻找：优先主视觉 box，其次最大的 box/lathe（杯/瓶无 box 也能拼接）
    if anchor is None:
        anchor = _max_anchor(spec)
    if anchor is not None:
        clamped = _clamp_attachments(spec, anchor)
        if clamped:
            log.info(f"3D 零部件尺寸钳制 {clamped} 个（吸管/把手等过大件兜底）")
        snapped = _snap_tubes(spec, anchor)
        if snapped:
            log.info(f"3D 插入件贴面拼接 {snapped} 个 tube（悬浮/脱离校正）")

    # 先验后校验（纯规则，不调 LLM）：surface none→建议值、贴图无材质关键词时材质兜底
    # （锦上添花：先验缺失/失败不改任何值，原逻辑不受影响）
    if prior:
        fixed = _prior_surface_check(spec, prior) + _prior_material_check(spec, prior)
        if fixed:
            log.info(f"3D 先验校验修正 {fixed} 处（surface）")

    # ===== Track G 质量分级：advanced 自纠；extreme 挂载点（B4 替换占位后启用） =====
    # 自纠/渲染任何异常 → 保留当前 spec 正常返回（失败不阻断，契约 C4-3）；
    # 补全只跑一次：自纠/渲染轮次内不重跑（契约 C4-7，补全已在第一轮后执行）。
    try:
        if mode in ("advanced", "extreme"):
            spec, effective_mode = _advanced_refine(spec, image_url)
        if mode == "extreme":
            if _extreme_refine is not None:  # B4 交付后启用真实闭环（返回 (spec, 实际模式)）
                spec, effective_mode = _extreme_refine(spec, image_url)
            else:
                effective_mode = "advanced"  # 渲染设施未就绪：降级（契约 C4-3）
                log.info("3D extreme 渲染设施未就绪，降级 advanced")
    except Exception as e:
        log.warning(f"3D 自纠/渲染阶段异常，保留当前 spec: {e}")

    log.info(f"3D 构建成功 item={spec.item} parts={len(spec.primitives)} "
             f"tick={spec.tick} model={result.provider}/{result.model} mode={effective_mode} "
             f"elapsed={int((time.perf_counter() - start) * 1000)}ms")
    return Build3dResponse(
        spec=spec,
        model_name=f"{result.provider}/{result.model}",
        degraded=result.degraded or (mode == "extreme" and effective_mode != "extreme"),
        error="",
        elapsed_ms=int((time.perf_counter() - start) * 1000),
        mode=effective_mode,
    )
