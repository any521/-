package com.dianfei.business.map.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 路线规划结果（GET /api/map/route）：折线坐标串已解析为 (lng,lat) 序列
 */
@Data
public class RouteVO {

    /** 总距离 米 */
    private BigDecimal distance;

    /** 预计耗时 分钟（高德 duration 秒 → 分钟，向上取整） */
    private Integer duration;

    /** 路线折线（lng,lat）序列，前端 AMap.Polyline 直接绘制 */
    private List<double[]> polyline;
}
