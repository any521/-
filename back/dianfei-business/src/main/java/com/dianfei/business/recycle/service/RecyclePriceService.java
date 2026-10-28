package com.dianfei.business.recycle.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.recycle.dto.RecyclePriceSaveDTO;
import com.dianfei.business.recycle.entity.RecyclePrice;
import com.dianfei.business.recycle.mapper.RecyclePriceMapper;
import com.dianfei.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 可回收物报价（B3）：用户端实时报价查询 + 后台 category 幂等 upsert 配置。
 * 与 biz_carbon_rule 一起支撑"新增字典品类+报价+规则，零代码即可下单获分"（09.3）。
 */
@Service
public class RecyclePriceService extends ServiceImpl<RecyclePriceMapper, RecyclePrice> {

    /** 用户端：生效中的报价列表（GET /api/recycle/price） */
    public List<RecyclePrice> listActive() {
        return lambdaQuery().eq(RecyclePrice::getStatus, 1).orderByAsc(RecyclePrice::getCategory).list();
    }

    /** 后台：全部报价（含下架） */
    public List<RecyclePrice> listAll() {
        return lambdaQuery().orderByAsc(RecyclePrice::getCategory).list();
    }

    /** 按品类查询（不存在返回 null，供行情同步读取旧价） */
    public RecyclePrice getByCategory(String category) {
        return lambdaQuery().eq(RecyclePrice::getCategory, category).one();
    }

    /** 后台配置：按 category 幂等 upsert */
    @Transactional
    public RecyclePrice saveByCategory(RecyclePriceSaveDTO dto) {
        if (dto.getPricePerKg().signum() < 0) {
            throw new BusinessException("报价不能为负");
        }
        RecyclePrice price = lambdaQuery().eq(RecyclePrice::getCategory, dto.getCategory()).one();
        if (price == null) {
            price = new RecyclePrice();
            price.setCategory(dto.getCategory());
        }
        price.setPricePerKg(dto.getPricePerKg());
        price.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        // 地区仅随行情落库写入；后台手工配置不传 city 时保留原值（避免误清上次查询地区）
        if (dto.getCity() != null && !dto.getCity().isBlank()) {
            price.setCity(dto.getCity());
        }
        // 显式刷新更新时间：strictUpdateFill 只填 null 字段，实体从库查出旧值会跳过填充，
        // 导致联网同步/后台改价后 update_time 停留旧时间（用户端「X · XX 更新」显示滞后）
        price.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(price);
        return price;
    }

    /** 校验品类已配置生效报价（下单/完成登记核算前置校验） */
    public RecyclePrice requireActive(String category) {
        RecyclePrice price = lambdaQuery()
                .eq(RecyclePrice::getCategory, category)
                .eq(RecyclePrice::getStatus, 1)
                .one();
        if (price == null) {
            throw new BusinessException("品类[" + category + "]未配置生效报价");
        }
        return price;
    }

    /**
     * 品类白名单归一化：前端小写 code / 语音 LLM 中文简称 / 英文核算码 → 统一落库编码。
     * 背景：报价表用「英文核算码(PAPER/PLASTIC/METAL/FABRIC/E_WASTE) + 中文展示行」双轨，
     * 前端表单提交小写 code（electronics），语音 LLM 可能输出任意值（如 electronices 错拼、
     * "纸箱,废纸,报纸"逗号串），requireActive 仅靠 MySQL 大小写不敏感匹配——electronics 与
     * E_WASTE/电子产品 完全不同名，必报「未配置生效报价」。归一化后新订单统一落英文核算码
     * （有英文行的品类）或中文行名（家电/玻璃/厨余，无英文行）；未命中别名的自定义品类保留原值，
     * 交由 requireActive 校验报价是否已配置（支持 09.3 零代码新增品类）。
     */
    private static final Map<String, String> CATEGORY_NORMALIZE = new HashMap<>();

    static {
        // 英文核算码自身
        for (String code : List.of("PAPER", "PLASTIC", "METAL", "FABRIC", "E_WASTE")) {
            CATEGORY_NORMALIZE.put(code, code);
        }
        // 前端小写 code → 英文核算码
        CATEGORY_NORMALIZE.put("paper", "PAPER");
        CATEGORY_NORMALIZE.put("plastic", "PLASTIC");
        CATEGORY_NORMALIZE.put("metal", "METAL");
        CATEGORY_NORMALIZE.put("fabric", "FABRIC");
        CATEGORY_NORMALIZE.put("electronics", "E_WASTE");
        // 无英文核算行的前端小写 code → 中文行名
        CATEGORY_NORMALIZE.put("appliance", "家电类");
        CATEGORY_NORMALIZE.put("glass", "玻璃类");
        CATEGORY_NORMALIZE.put("kitchen", "厨余垃圾");
        // 中文行名 → 有英文行的统一英文核算码，无英文行保持中文行名
        CATEGORY_NORMALIZE.put("废纸类", "PAPER");
        CATEGORY_NORMALIZE.put("塑料类", "PLASTIC");
        CATEGORY_NORMALIZE.put("金属类", "METAL");
        CATEGORY_NORMALIZE.put("织物类", "FABRIC");
        CATEGORY_NORMALIZE.put("电子产品", "E_WASTE");
        CATEGORY_NORMALIZE.put("家电类", "家电类");
        CATEGORY_NORMALIZE.put("玻璃类", "玻璃类");
        CATEGORY_NORMALIZE.put("厨余垃圾", "厨余垃圾");
        // 语音 LLM 中文简称别名（与 voice prompt 品类枚举同源，防全称/简称漂移）
        CATEGORY_NORMALIZE.put("纸类", "PAPER");
        CATEGORY_NORMALIZE.put("塑料", "PLASTIC");
        CATEGORY_NORMALIZE.put("金属", "METAL");
        CATEGORY_NORMALIZE.put("织物", "FABRIC");
        CATEGORY_NORMALIZE.put("电子", "E_WASTE");
        CATEGORY_NORMALIZE.put("家电", "家电类");
        CATEGORY_NORMALIZE.put("玻璃", "玻璃类");
        CATEGORY_NORMALIZE.put("厨余", "厨余垃圾");
    }

    /** 品类归一化 + 白名单校验：任意来源 → 统一落库编码；白名单外明确报错（不产生「未配置生效报价」误导） */
    public String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new BusinessException("回收品类不能为空");
        }
        String trimmed = category.trim();
        String normalized = CATEGORY_NORMALIZE.get(trimmed);
        if (normalized == null) {
            // 大小写兜底：ELECTRONICS/Paper 等任意大小写形态
            normalized = CATEGORY_NORMALIZE.get(trimmed.toUpperCase(Locale.ROOT));
        }
        if (normalized == null) {
            // 自定义品类不做硬编码白名单限制，是否可用由后续 requireActive 的生效报价决定
            return trimmed;
        }
        return normalized;
    }

    /** 按报价核算金额：weight * pricePerKg，保留 2 位（品类先归一化，完成登记入口同样防脏值） */
    public BigDecimal settle(String category, BigDecimal weight) {
        return requireActive(normalizeCategory(category)).getPricePerKg()
                .multiply(weight)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

