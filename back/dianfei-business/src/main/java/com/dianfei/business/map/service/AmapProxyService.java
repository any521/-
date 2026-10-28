package com.dianfei.business.map.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.dianfei.business.map.vo.GeocodeVO;
import com.dianfei.business.map.vo.RouteVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.RedisUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 高德 Web 服务 API 代理（onsite04）：geocode / regeo / route（driving|cycling|walking）。
 * HTTP 客户端选型：项目无既有 HTTP 客户端依赖，采用 RestTemplate（spring-web 随 starter-web 提供，
 * 无额外依赖），统一 5s 连接/读取超时；结果 Redis 缓存 key `map:{api}:{paramsHash}` TTL 10min 防配额耗尽；
 * 高德 status!=1 / 网络异常 → 统一 5001「地图服务不可用」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmapProxyService {

    private static final String REST_BASE = "https://restapi.amap.com/v3";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final AmapProperties amapProperties;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    /** 项目无既有 RestTemplate Bean，本地构建（5s 连接 + 5s 读取超时） */
    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    /** 服务端 IP → 省/市定位（AMap /v3/ip，供每日报价行情按当地城市搜索；缓存 1 天） */
    public GeocodeVO ipLocation() {
        String cacheKey = cacheKey("ip", "location");
        String cached = redisUtils.get(cacheKey);
        if (cached != null) {
            return parseJson(cached, GeocodeVO.class);
        }
        JsonNode root = call("ip");
        GeocodeVO vo = new GeocodeVO();
        vo.setProvince(root.path("province").asText(null));
        vo.setCity(root.path("city").asText(null));
        vo.setDistrict(root.path("adcode").asText(null));
        redisUtils.set(cacheKey, toJson(vo), Duration.ofDays(1));
        return vo;
    }

    /** 地址 → 经纬度（缓存 key map:geocode:{md5(address)}） */
    public GeocodeVO geocode(String address) {
        String cacheKey = cacheKey("geocode", address);
        String cached = redisUtils.get(cacheKey);
        if (cached != null) {
            return parseJson(cached, GeocodeVO.class);
        }
        JsonNode root = call("geocode/geo", "address", address);
        // 高德地理编码：geocodes[0].location = "lng,lat"，formatted_address
        JsonNode first = root.path("geocodes").path(0);
        if (first.isMissingNode() || first.path("location").asText().isBlank()) {
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "未找到该地址");
        }
        String[] lngLat = first.path("location").asText().split(",");
        GeocodeVO vo = new GeocodeVO();
        vo.setLng(new BigDecimal(lngLat[0]));
        vo.setLat(new BigDecimal(lngLat[1]));
        vo.setFormattedAddress(first.path("formatted_address").asText(null));
        vo.setProvince(first.path("province").asText(null));
        vo.setCity(first.path("city").asText(null));
        vo.setDistrict(first.path("district").asText(null));
        redisUtils.set(cacheKey, toJson(vo), CACHE_TTL);
        return vo;
    }

    /** 经纬度 → 逆地理（可选能力，缓存 key map:regeo:{lng},{lat}） */
    public GeocodeVO regeo(BigDecimal lng, BigDecimal lat) {
        String raw = lng.stripTrailingZeros().toPlainString() + "," + lat.stripTrailingZeros().toPlainString();
        String cacheKey = cacheKey("regeo", raw);
        String cached = redisUtils.get(cacheKey);
        if (cached != null) {
            return parseJson(cached, GeocodeVO.class);
        }
        // 高德 v3 逆地理路径为 /v3/geocode/regeo（非 /v3/regeo，拼错会 10002 SERVICE_NOT_AVAILABLE）
        JsonNode root = call("geocode/regeo", "location", raw);
        JsonNode regeocode = root.path("regeocode");
        if (regeocode.isMissingNode()) {
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "逆地理解析失败");
        }
        GeocodeVO vo = new GeocodeVO();
        vo.setLng(lng);
        vo.setLat(lat);
        vo.setFormattedAddress(regeocode.path("formatted_address").asText(null));
        // addressComponent 提供省/市/区，供前端定位后填充地址簿（城市为空数组的直辖市场景前端降级用省名）
        JsonNode comp = regeocode.path("addressComponent");
        vo.setProvince(comp.path("province").asText(null));
        vo.setCity(comp.path("city").asText(null));
        vo.setDistrict(comp.path("district").asText(null));
        // D0 片区自动创建：乡镇/街道 + 村/社区（精确到村）+ 小区/村庄/学校等兴趣点名
        vo.setTownship(comp.path("township").asText(null));
        // 高德 addressComponent 无村字段：从格式化地址中乡镇之后提取「XX村/XX社区」（农村地区 neighborhoods 常为空）
        vo.setVillage(extractVillage(formattedAddress(vo), vo.getTownship()));
        String hood = firstText(comp.path("neighborhoods").path("name"), comp.path("buildings").path("name"));
        vo.setNeighborhood(StrUtil.isBlank(hood) ? null : hood);
        redisUtils.set(cacheKey, toJson(vo), CACHE_TTL);
        return vo;
    }

    /** 从格式化地址中提取村/社区名：定位到乡镇名之后，取首个「XX村/XX社区/XX庄/XX屯」（长度 2-12） */
    private String extractVillage(String formattedAddress, String township) {
        if (StrUtil.isBlank(formattedAddress)) {
            return null;
        }
        String rest = formattedAddress;
        if (StrUtil.isNotBlank(township)) {
            int idx = formattedAddress.indexOf(township);
            if (idx >= 0) {
                rest = formattedAddress.substring(idx + township.length());
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([\\u4e00-\\u9fa5]{2,12}?(?:村|村委会|社区|庄|屯))")
                .matcher(rest);
        return m.find() ? m.group(1) : null;
    }

    private String formattedAddress(GeocodeVO vo) {
        return vo.getFormattedAddress();
    }

    /** 路线规划：mode=driving|cycling|walking（缓存 key map:route:{mode}:{md5(起终点)}） */
    public RouteVO route(BigDecimal originLng, BigDecimal originLat,
                         BigDecimal destLng, BigDecimal destLat, String mode) {
        String origin = originLng.stripTrailingZeros().toPlainString() + "," + originLat.stripTrailingZeros().toPlainString();
        String dest = destLng.stripTrailingZeros().toPlainString() + "," + destLat.stripTrailingZeros().toPlainString();
        String cacheKey = cacheKey("route:" + mode, origin + ">" + dest);
        String cached = redisUtils.get(cacheKey);
        if (cached != null) {
            return parseJson(cached, RouteVO.class);
        }
        String api = switch (mode) {
            case "cycling" -> "direction/cycling";
            case "walking" -> "direction/walking";
            default -> "direction/driving";
        };
        JsonNode root = call(api, "origin", origin, "destination", dest);
        JsonNode path = root.path("route").path("paths").path(0);
        if (path.isMissingNode()) {
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "路线规划失败");
        }
        RouteVO vo = new RouteVO();
        vo.setDistance(new BigDecimal(path.path("distance").asLong()));
        // 高德 duration 单位秒 → 分钟（向上取整）
        vo.setDuration((int) Math.ceil(path.path("duration").asDouble() / 60.0));
        vo.setPolyline(parsePolyline(path.path("steps")));
        redisUtils.set(cacheKey, toJson(vo), CACHE_TTL);
        return vo;
    }

    /** 取第一个非空文本节点（高德空值可能是空数组 [] 或空串） */
    private String firstText(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && n.isTextual() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
    }

    /** 高德 steps[].polyline = "lng,lat;lng,lat;..." → (lng,lat) 序列（去重相邻点） */
    private List<double[]> parsePolyline(JsonNode steps) {
        List<double[]> points = new ArrayList<>();
        for (JsonNode step : steps) {
            String polyline = step.path("polyline").asText("");
            if (polyline.isBlank()) {
                continue;
            }
            for (String seg : polyline.split(";")) {
                String[] lngLat = seg.split(",");
                if (lngLat.length != 2) {
                    continue;
                }
                double[] point = {Double.parseDouble(lngLat[0]), Double.parseDouble(lngLat[1])};
                if (points.isEmpty() || points.get(points.size() - 1)[0] != point[0] || points.get(points.size() - 1)[1] != point[1]) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    /** 统一调用：携带 server_key（+ 可选安全密钥 jscode）调高德 REST，status!=1 或网络异常 → 5001 */
    private JsonNode call(String api, String... queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(REST_BASE + "/" + api)
                .queryParam("key", amapProperties.serverKey())
                .queryParam("extensions", "all");
        // V37：高德新版 Web 服务 API 要求 key + jscode（安全密钥）配对；
        // 已配置安全密钥时随请求追加，未配置则不追加（保持 key 直连兼容）。
        String jscode = amapProperties.securityCode();
        if (StrUtil.isNotBlank(jscode)) {
            builder.queryParam("jscode", jscode);
        }
        for (int i = 0; i + 1 < queryParams.length; i += 2) {
            builder.queryParam(queryParams[i], queryParams[i + 1]);
        }
        String json;
        try {
            json = restTemplate.getForObject(builder.build().toUri(), String.class);
        } catch (Exception e) {
            log.warn("[amap] 请求失败 api={} msg={}", api, e.getMessage());
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "地图服务不可用");
        }
        if (StrUtil.isBlank(json)) {
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "地图服务不可用");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("[amap] 响应解析失败 api={}", api);
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "地图服务不可用");
        }
        if (!"1".equals(root.path("status").asText())) {
            log.warn("[amap] 业务失败 api={} info={}", api, root.path("info").asText());
            throw new BusinessException(ResultCode.MAP_SERVICE_UNAVAILABLE, "地图服务不可用");
        }
        return root;
    }

    private String cacheKey(String api, String params) {
        return "map:" + api + ":" + DigestUtil.md5Hex(params);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    private <T> T parseJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("缓存反序列化失败", e);
        }
    }
}
