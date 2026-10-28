"""services.forecast_service · F9 时空预测（Prophet 优先，移动平均+周期因子兜底）
services.pricing_service 合并于此 · F5 动态定价（LLM 建议 + 边界夹逼，人工可回滚）

F9 三级降级链：Prophet（历史充足且已安装）→ 移动平均+时段周期因子（仅需历史）
→ 确定性季节序列（无历史，degraded=true）；历史由 Java 侧随请求回填（无 DB 直连）。
"""
from __future__ import annotations

import math
from collections import defaultdict
from datetime import datetime, timedelta

from app.core import llm_router
from app.core.llm_router import AiRouteExhausted
from app.core.logging import get_logger
from app.schemas.forecast import (ForecastPoint, ForecastRequest, ForecastResponse,
                                  PricingRequest, PricingResponse)

log = get_logger("forecast_service")

# ==================== RAG 检索接入（RAG-03 F5，契约 C2；RAG-02 未交付/失败一律静默降级） ====================
try:
    from app.rag import retriever
    _RAG_OK = True
except Exception:          # 模块缺失（RAG-02 未交付）或依赖缺失
    retriever = None
    _RAG_OK = False

# 基准价提示词（命中检索价后追加到 system prompt 尾部；只追加不删改原文）
_PRICE_HINT = "若提供真实基准价，价格建议必须落在基准价区间 ±20% 内；无基准价时按平台基础价。"


def _price_lookup(category: str, region: str | None = None) -> dict | None:
    """真实基准价检索：失败/未就绪返回 None；命中进结构化日志"""
    if not _RAG_OK:
        return None
    try:
        hit = retriever.price_lookup(category=category, region=region)
        if hit:
            log.info(f"RAG 基准价命中 category={hit.get('category')} "
                     f"price={hit.get('min_price')}~{hit.get('max_price')} "
                     f"data_source={hit.get('data_source')}")
        return hit
    except Exception as e:
        log.warning(f"RAG 基准价检索降级: {e}")
        return None


# F5 安全边界：AI 给出的系数超出即夹逼（人工干预入口在 Java 侧 sys_config）
FACTOR_MIN, FACTOR_MAX = 0.8, 1.2

# F9 序列粒度：6h 桶（与 ForecastPoint.offset_hours 口径一致）
_BUCKET_HOURS = 6
# Prophet 最少需要的历史桶数（不足时直接走周期因子，避免过拟合）
_MIN_PROPHET_BUCKETS = 16


def _bucketize(req: ForecastRequest) -> dict[datetime, float]:
    """历史点聚到 6h 桶（桶起点对齐 0/6/12/18 点），返回桶起点→kg 总量"""
    buckets: dict[datetime, float] = defaultdict(float)
    for p in req.history or []:
        start = p.ts.replace(minute=0, second=0, microsecond=0,
                             hour=p.ts.hour // _BUCKET_HOURS * _BUCKET_HOURS)
        buckets[start] += p.kg
    return dict(buckets)


def _hot_level(avg_kg: float, peak_kg: float) -> int:
    """热力等级 1-5：预测均量相对历史峰值的占比分档（供大屏与派单）"""
    if peak_kg <= 0:
        return 3
    return min(5, max(1, math.ceil(avg_kg / peak_kg * 5)))


def _prophet_forecast(req: ForecastRequest) -> ForecastResponse | None:
    """Prophet 路径：历史桶数达标且 prophet/pandas 可用时训练预测；否则 None"""
    buckets = _bucketize(req)
    if len(buckets) < _MIN_PROPHET_BUCKETS:
        return None
    try:
        import pandas as pd
        from prophet import Prophet
    except ImportError:
        return None
    try:
        df = pd.DataFrame({"ds": list(buckets.keys()), "y": list(buckets.values())})
        model = Prophet(daily_seasonality=True, weekly_seasonality=True,
                        yearly_seasonality=False)
        model.fit(df)
        steps = max(req.horizon_hours // _BUCKET_HOURS, 1)
        future = model.make_future_dataframe(
            periods=steps, freq=f"{_BUCKET_HOURS}h", include_history=False)
        pred = model.predict(future)
        series = [ForecastPoint(offset_hours=i * _BUCKET_HOURS,
                                predicted_kg=round(max(r["yhat"], 0), 1),
                                lower=round(max(r["yhat_lower"], 0), 1),
                                upper=round(max(r["yhat_upper"], 0), 1))
                  for i, (_, r) in enumerate(pred.iterrows())]
        avg = sum(p.predicted_kg for p in series) / len(series)
        return ForecastResponse(series=series, model_desc="prophet-v1",
                                hot_level=_hot_level(avg, max(buckets.values())),
                                degraded=False)
    except Exception as e:  # Prophet 训练/预测异常不阻断，交给下一级
        log.warning(f"F9 Prophet 异常降级周期因子: {e}")
        return None


def _periodic_forecast(req: ForecastRequest) -> ForecastResponse | None:
    """移动平均+时段周期因子：按 6h 时段聚历史均值外推（真实数据，非降级）"""
    buckets = _bucketize(req)
    if not buckets:
        return None
    # 按“一天内第几个 6h 时段”聚均值；缺样本时段回退全局均值
    slot_sum: dict[int, float] = defaultdict(float)
    slot_cnt: dict[int, int] = defaultdict(int)
    for start, kg in buckets.items():
        slot = start.hour // _BUCKET_HOURS
        slot_sum[slot] += kg
        slot_cnt[slot] += 1
    slot_mean = {s: slot_sum[s] / slot_cnt[s] for s in slot_sum}
    overall = sum(buckets.values()) / len(buckets)

    now = datetime.now()
    series = []
    for h in range(0, req.horizon_hours, _BUCKET_HOURS):
        slot = ((now + timedelta(hours=h)).hour // _BUCKET_HOURS) % (24 // _BUCKET_HOURS)
        kg = slot_mean.get(slot, overall)
        series.append(ForecastPoint(offset_hours=h, predicted_kg=round(kg, 1),
                                    lower=round(kg * 0.7, 1), upper=round(kg * 1.3, 1)))
    avg = sum(p.predicted_kg for p in series) / len(series)
    return ForecastResponse(series=series, model_desc="moving-average-periodic",
                            hot_level=_hot_level(avg, max(buckets.values())),
                            degraded=False)


def _moving_average_fallback(req: ForecastRequest) -> ForecastResponse:
    """无历史兜底：mock 供应商的确定性季节性序列（degraded，可联调）"""
    from app.core import mock_models
    raw = mock_models.generate("forecast", req.model_dump(exclude={"history"}))
    return ForecastResponse(
        series=[ForecastPoint(**p) for p in raw["series"]],
        model_desc="moving-average", hot_level=raw["hot_level"], degraded=True)


def forecast(req: ForecastRequest) -> ForecastResponse:
    resp = _prophet_forecast(req)
    if resp is not None:
        return resp
    resp = _periodic_forecast(req)
    if resp is not None:
        log.info(f"F9 area={req.area_id} 周期因子模型（Prophet 未安装或历史不足）")
        return resp
    log.info(f"F9 area={req.area_id} 无历史序列，使用确定性兜底模型")
    return _moving_average_fallback(req)


def pricing(req: PricingRequest) -> PricingResponse:
    # RAG-03 F5 真实基准价检索：invoke 前执行一次；命中注入 prompt 上下文与兜底路径
    # （PricingRequest 无 region 字段，region 缺省时 price_lookup 取该品类首条记录）
    hit = _price_lookup(req.category)
    system_content = ('输出定价建议 JSON：{"price_factor":..,"points_factor":..,'
                      '"reason":..,"valid_hours":..}，系数区间 0.8-1.2')
    user_content = (f"片区{req.area_id} 品类{req.category} 基础价{req.base_price} "
                    f"近7日供给{req.recent_supply_kg}kg 需求{req.recent_demand_kg}kg")
    if hit:
        system_content += _PRICE_HINT
        user_content += (f"\n【真实基准价参考】品类={req.category} "
                         f"地区={hit.get('region') or hit.get('base_region') or '全国'} "
                         f"价格区间={hit.get('min_price')}~{hit.get('max_price')} {hit.get('price_unit') or ''} "
                         f"数据来源={hit.get('data_source')}（采集于 {hit.get('base_price_date')}，"
                         f"当前报价需结合时效）")
    try:
        result = llm_router.invoke(
            capability="pricing",
            messages=[
                {"role": "system", "content": system_content},
                {"role": "user", "content": user_content},
            ],
            response_model=PricingResponse,
            context=req.model_dump(),
        )
        parsed: PricingResponse = result.parsed
        # 边界夹逼：防模型给出离谱系数
        parsed.price_factor = min(max(parsed.price_factor, FACTOR_MIN), FACTOR_MAX)
        parsed.points_factor = min(max(parsed.points_factor, FACTOR_MIN), FACTOR_MAX)
        parsed.degraded = result.degraded
        return parsed
    except AiRouteExhausted:
        log.warning("F5 路由链耗尽，回落 BASE_PRICE")
        if hit and hit.get("min_price") is not None:
            # 命中检索价：价格区间进 reason（Schema 冻结，复用现有字段，不新增）
            reason = (f"AI 不可用，回落基础价；真实基准价区间 "
                      f"{hit['min_price']}~{hit['max_price']} {hit.get('price_unit') or ''}")
            log.info(f"F5 兜底引用检索价 {reason}")
            return PricingResponse(price_factor=1.0, points_factor=1.0,
                                   reason=reason, valid_hours=0, degraded=True)
        return PricingResponse(price_factor=1.0, points_factor=1.0,
                               reason="AI 不可用，回落基础价", valid_hours=0, degraded=True)
