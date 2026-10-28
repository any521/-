package com.dianfei.business.map.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 回收站点出参（GET /api/stations，全量 ≤50 条）
 */
@Data
public class StationVO {

    /** 雪花 ID 字符串（防前端精度丢失） */
    private String id;

    private String name;

    /** RECYCLE / KITCHEN / COLLECT */
    private String stationType;

    private BigDecimal lng;

    private BigDecimal lat;

    private String address;

    private String openHours;
}
