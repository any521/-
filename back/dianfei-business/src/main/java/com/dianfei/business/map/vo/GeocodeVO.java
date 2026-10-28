package com.dianfei.business.map.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 地理编码结果（GET /api/map/geocode）
 */
@Data
public class GeocodeVO {

    private BigDecimal lng;

    private BigDecimal lat;

    /** 结构化地址（高德 formatted_address） */
    private String formattedAddress;

    /** 省（高德 addressComponent，直辖市时 city 可能为空，前端用省兜底） */
    private String province;

    /** 市（直辖市可能为空数组，前端降级用省名） */
    private String city;

    /** 区/县 */
    private String district;

    /** 乡镇/街道（D0 片区自动创建：省/市/县/镇/村 层级） */
    private String township;

    /** 村/社区（从格式化地址中乡镇之后提取，无则 null；片区自动创建精确到村） */
    private String village;

    /** 小区/村庄/学校/公司等兴趣点名（高德 neighborhoods/buildings 首选） */
    private String neighborhood;
}
