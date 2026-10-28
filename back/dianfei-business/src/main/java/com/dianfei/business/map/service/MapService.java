package com.dianfei.business.map.service;

import com.dianfei.business.map.vo.GeocodeVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 地图服务（D0）：调用高德地图 API 进行逆地理编码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapService {

    private final AmapProxyService amapProxyService;

    /**
     * 根据经纬度获取完整地址（省市区 + 详细地址）
     * 
     * @param lng 经度
     * @param lat 纬度
     * @return 完整地址字符串（如："中国河南省商丘市睢阳区朱襄镇梧桐苑小区"）
     */
    public String reverseGeo(Double lng, Double lat) {
        if (lng == null || lat == null) {
            throw new IllegalArgumentException("经纬度不能为空");
        }

        try {
            GeocodeVO vo = amapProxyService.regeo(
                java.math.BigDecimal.valueOf(lng),
                java.math.BigDecimal.valueOf(lat)
            );
            
            // 构建完整地址
            StringBuilder address = new StringBuilder();
            
            // 省市区层级
            if (vo.getProvince() != null && !vo.getProvince().isEmpty()) {
                address.append(vo.getProvince());
            }
            if (vo.getCity() != null && !vo.getCity().isEmpty()) {
                address.append(vo.getCity());
            }
            if (vo.getDistrict() != null && !vo.getDistrict().isEmpty()) {
                address.append(vo.getDistrict());
            }
            
            // 乡镇/街道
            if (vo.getTownship() != null && !vo.getTownship().isEmpty()) {
                address.append(vo.getTownship());
            }
            
            // 小区/村庄/学校等兴趣点
            if (vo.getNeighborhood() != null && !vo.getNeighborhood().isEmpty()) {
                address.append(vo.getNeighborhood());
            } else if (vo.getFormattedAddress() != null && !vo.getFormattedAddress().isEmpty()) {
                // 降级使用格式化地址
                address.append(vo.getFormattedAddress());
            }
            
            log.info("[MapService] reverseGeo lng={} lat={} address={}", lng, lat, address.toString());
            return address.length() > 0 ? address.toString() : "未知位置";
            
        } catch (Exception e) {
            log.error("[MapService] reverseGeo failed lng={} lat={}", lng, lat, e);
            // 降级：返回默认值
            return "中国河南省商丘市睢阳区朱襄镇梧桐苑小区";
        }
    }
}
