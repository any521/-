package com.dianfei.business.staff.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 今日智能路线（GET /api/staff/route/today）：无订单时 stops=[]、totalKm=0、totalMin=0
 */
@Data
public class RoutePlanVO {

    /** 总行驶里程 km（1 位小数） */
    private BigDecimal totalKm;

    /** 预估总耗时 分钟（行驶 + 每单服务 20 分钟） */
    private Integer totalMin;

    /** 调度算法：预约时间贪心 */
    private String algorithm;

    private List<RouteStopVO> stops;
}
