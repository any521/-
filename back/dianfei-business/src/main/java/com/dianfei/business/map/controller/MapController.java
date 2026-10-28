package com.dianfei.business.map.controller;

import com.dianfei.business.map.service.AmapProperties;
import com.dianfei.business.map.service.AmapProxyService;
import com.dianfei.business.map.vo.GeocodeVO;
import com.dianfei.business.map.vo.MapConfigVO;
import com.dianfei.business.map.vo.RouteVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 地图服务代理（onsite04，缺口 G4）：配置下发 / 地理编码 / 逆地理 / 路线规划。
 * 全部需登录（SecurityConfig anyRequest authenticated）；server_key 严禁出网，
 * 高德失败统一 5001「地图服务不可用」，前端 Toast 降级。
 */
@Tag(name = "地图服务")
@RestController
@RequestMapping("/api/map")
@Validated
@RequiredArgsConstructor
public class MapController {

    private final AmapProperties amapProperties;
    private final AmapProxyService amapProxyService;

    @Operation(summary = "地图配置下发（web_key + security_code；未配置 web_key → 5001）")
    @GetMapping("/config")
    public Result<MapConfigVO> config() {
        MapConfigVO vo = new MapConfigVO();
        vo.setWebKey(amapProperties.webKey());
        vo.setSecurityCode(amapProperties.securityCode());
        return Result.success(vo);
    }

    @Operation(summary = "地址地理编码（→ 经纬度）")
    @GetMapping("/geocode")
    public Result<GeocodeVO> geocode(@RequestParam @NotBlank String address) {
        return Result.success(amapProxyService.geocode(address));
    }

    @Operation(summary = "经纬度逆地理（→ 地址）")
    @GetMapping("/regeo")
    public Result<GeocodeVO> regeo(@RequestParam BigDecimal lng, @RequestParam BigDecimal lat) {
        return Result.success(amapProxyService.regeo(lng, lat));
    }

    @Operation(summary = "路线规划（driving|cycling|walking，返回折线+距离+耗时）")
    @GetMapping("/route")
    public Result<RouteVO> route(@RequestParam BigDecimal originLng,
                                 @RequestParam BigDecimal originLat,
                                 @RequestParam BigDecimal destLng,
                                 @RequestParam BigDecimal destLat,
                                 @RequestParam(defaultValue = "driving") String mode) {
        return Result.success(amapProxyService.route(originLng, originLat, destLng, destLat, mode));
    }
}
