package com.dianfei.business.map.vo;

import lombok.Data;

/**
 * 地图配置下发（GET /api/map/config）：仅 web_key 与 security_code，server_key 严禁出网。
 * security_code（V37）：JS API 2.0 加载时注入 window._AMapSecurityConfig.securityJsCode，
 * 与 web_key 配对使用；为空时前端不注入（兼容旧式 key 直连）。
 */
@Data
public class MapConfigVO {

    /** 高德 Web 端 JS API 2.0 Key（未配置时接口返回 5001） */
    private String webKey;

    /** 高德安全密钥（jscode / securityJsCode），可为空 */
    private String securityCode;
}
