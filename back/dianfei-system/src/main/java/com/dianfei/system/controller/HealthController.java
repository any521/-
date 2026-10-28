package com.dianfei.system.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查端点（S1 部署可观测）：供 docker compose healthcheck / 负载均衡探活 / 监控告警使用。
 * <p>DB + Redis 均可用才返回 UP（HTTP 200），任一不可用返回 DOWN（HTTP 503）；
 * 不依赖 actuator，轻量直连探活。
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = checkDb();
        boolean redisUp = checkRedis();
        boolean up = dbUp && redisUp;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DOWN");
        body.put("db", dbUp ? "UP" : "DOWN");
        body.put("redis", redisUp ? "UP" : "DOWN");
        body.put("time", LocalDateTime.now().format(FMT));
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean checkDb() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(one);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRedis() {
        try {
            // 写探针 key（5 秒自过期）：set 成功即 Redis 可用。
            // 不能用 hasKey——key 不存在时返回 false 属正常，会造成误报 DOWN
            redisTemplate.opsForValue().set(
                    "__dianfei_health__", "1", java.time.Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
