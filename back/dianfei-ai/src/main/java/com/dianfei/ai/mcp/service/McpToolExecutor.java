package com.dianfei.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.ai.mcp.entity.McpRecyclePrice;
import com.dianfei.ai.mcp.mapper.McpRecyclePriceMapper;
import com.dianfei.business.carbon.service.CarbonQueryService;
import com.dianfei.business.carbon.service.CarbonVerifyService;
import com.dianfei.business.mall.dto.MallExchangeDTO;
import com.dianfei.business.mall.service.MallExchangeService;
import com.dianfei.business.recycle.dto.RecycleOrderCreateDTO;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.recycle.vo.RecycleOrderVO;
import com.dianfei.business.repair.linkage.entity.LinkedRecycleOrder;
import com.dianfei.business.repair.linkage.mapper.LinkedRecycleOrderMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具执行器（08 章 8.3）：转发到与人类/Agent 共用的同一批业务服务，
 * 仅鉴权与审计标记不同（principal_type=MCP）；本类只做参数标准化与调用，不做鉴权。
 */
@Service
@RequiredArgsConstructor
public class McpToolExecutor {

    private static final DateTimeFormatter APPOINT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CarbonQueryService carbonQueryService;
    private final CarbonVerifyService carbonVerifyService;
    private final McpRecyclePriceMapper recyclePriceMapper;
    private final LinkedRecycleOrderMapper recycleOrderMapper;
    private final MallExchangeService mallExchangeService;
    private final RecycleOrderService recycleOrderService;

    /** 按工具名分发执行，返回结构化结果（进 Result.data 与审计摘要） */
    public Object execute(String tool, Long userId, Map<String, Object> params) {
        return switch (tool) {
            case "query_carbon_account" -> carbonQueryService.account(userId);
            case "list_recycling_orders" -> listRecyclingOrders(userId, params);
            case "get_recycling_price" -> getRecyclingPrice(params);
            case "get_carbon_certificate" -> carbonVerifyService.verifyOwned(requireLong(params, "log_id"), userId);
            case "book_recycling" -> bookRecycling(userId, params);
            case "redeem_points" -> redeemPoints(userId, params);
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "未知 MCP 工具：" + tool);
        };
    }

    private List<LinkedRecycleOrder> listRecyclingOrders(Long userId, Map<String, Object> params) {
        long limit = Math.max(1L, Math.min(optionalLong(params, "limit", 10L), 20L));
        return recycleOrderMapper.selectList(new LambdaQueryWrapper<LinkedRecycleOrder>()
                .eq(LinkedRecycleOrder::getUserId, userId)
                .orderByDesc(LinkedRecycleOrder::getId)
                .last("LIMIT " + limit));
    }

    private List<McpRecyclePrice> getRecyclingPrice(Map<String, Object> params) {
        String category = optionalText(params, "category");
        return recyclePriceMapper.selectList(new LambdaQueryWrapper<McpRecyclePrice>()
                .eq(McpRecyclePrice::getStatus, 1)
                .eq(category != null, McpRecyclePrice::getCategory, category));
    }

    /** 创建回收预约（二次确认通过后才会走到这里）；片区缺省 1，待 B 线用户默认片区就绪后替换 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bookRecycling(Long userId, Map<String, Object> params) {
        RecycleOrderCreateDTO dto = new RecycleOrderCreateDTO();
        long areaId = optionalLong(params, "area_id", 1L);
        long quantity = optionalLong(params, "quantity", 1L);
        if (areaId <= 0 || quantity <= 0 || quantity > 10000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "area_id/quantity 参数超出范围");
        }
        dto.setAreaId(areaId);
        dto.setCategory(requireText(params, "category"));
        dto.setQuantity((int) quantity);
        String address = requireText(params, "address");
        if (address.length() > 255) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "address 长度不能超过 255 个字符");
        }
        dto.setAddress(address);
        dto.setAppointTime(parseAppointTime(requireText(params, "appoint_time")));
        RecycleOrderVO order = recycleOrderService.create(userId, dto);
        return Map.of("order_id", order.getId(), "order_no", order.getOrderNo(),
                "status", "PENDING_ACCEPT");
    }

    /** 积分兑换（二次确认通过后执行；余额不足由账本抛 2006） */
    public Object redeemPoints(Long userId, Map<String, Object> params) {
        MallExchangeDTO dto = new MallExchangeDTO();
        dto.setProductId(requireLong(params, "product_id"));
        long quantity = optionalLong(params, "quantity", 1L);
        if (quantity <= 0 || quantity > 1000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "quantity 必须在 1 到 1000 范围内");
        }
        dto.setQuantity((int) quantity);
        dto.setAddress(optionalText(params, "address"));
        return mallExchangeService.exchange(userId, dto);
    }

    private LocalDateTime parseAppointTime(String text) {
        try {
            return LocalDateTime.parse(text, APPOINT_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "appoint_time 格式应为 yyyy-MM-dd HH:mm");
        }
    }

    private String requireText(Map<String, Object> params, String key) {
        String value = optionalText(params, key);
        if (value == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少必填参数：" + key);
        }
        return value;
    }

    private String optionalText(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isEmpty() ? null : text;
    }

    private long requireLong(Map<String, Object> params, String key) {
        String text = requireText(params, key);
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, key + " 必须为数字");
        }
    }

    private long optionalLong(Map<String, Object> params, String key, long defaultValue) {
        String text = optionalText(params, key);
        if (text == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, key + " 必须为数字");
        }
    }
}
