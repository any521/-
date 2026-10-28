package com.dianfei.business.map.service;

import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 高德地图配置（onsite04）：从 sys_config 热读 amap.web_key / amap.server_key / amap.security_code，
 * 管理端「参数配置」页 PUT 即生效（ConfigService 缓存同键刷新），无需重启。
 */
@Component
@RequiredArgsConstructor
public class AmapProperties {

    public static final String KEY_WEB = "amap.web_key";
    public static final String KEY_SERVER = "amap.server_key";
    public static final String KEY_SECURITY = "amap.security_code";

    private final ConfigService configService;

    /** Web 端 JS API Key（缺失 → 5001，前端降级提示） */
    public String webKey() {
        String key = configService.getValue(KEY_WEB, "");
        if (key.isBlank()) {
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "地图未配置（amap.web_key）");
        }
        return key;
    }

    /** 服务端 Web 服务 Key（缺失 → 5001，仅供后端代理使用，严禁下发前端） */
    public String serverKey() {
        String key = configService.getValue(KEY_SERVER, "");
        if (key.isBlank()) {
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "地图未配置（amap.server_key）");
        }
        return key;
    }

    /** 高德安全密钥（jscode / securityJsCode，V37）：key 配对使用；缺失时调用方不追加 jscode 参数 */
    public String securityCode() {
        return configService.getValue(KEY_SECURITY, "");
    }
}
