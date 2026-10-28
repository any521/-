package com.dianfei.business.map.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 最近垃圾站点 + 最优路线出参（GET /api/stations/nearest）
 */
@Data
public class NearestStationVO {

    /** 雪花 ID 字符串（防前端精度丢失） */
    private String id;

    private String name;

    /** RECYCLE / KITCHEN / COLLECT */
    private String stationType;

    private BigDecimal lng;

    private BigDecimal lat;

    private String address;

    private String openHours;

    /** 步行距离 米（路线规划不可用时降级为直线距离） */
    private BigDecimal distance;

    /** 预计耗时 分钟（路线规划不可用时为 null） */
    private Integer duration;

    /** 路线折线（lng,lat）序列；路线规划不可用时为 null（前端画直线） */
    private List<double[]> polyline;
}
