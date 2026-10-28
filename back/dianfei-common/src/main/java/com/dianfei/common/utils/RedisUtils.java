package com.dianfei.common.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis 工具：封装常用操作；SETNX 供幂等/分布式锁使用
 */
@Component
public class RedisUtils {

    private final StringRedisTemplate redisTemplate;

    public RedisUtils(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ---------- string ----------

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** Redis 6.2 GETDEL：原子读取并删除一次性凭据。 */
    public String getAndDelete(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean expire(String key, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout));
    }

    /** SETNX：不存在则设置，供幂等与互斥锁 */
    public boolean setIfAbsent(String key, String value, Duration timeout) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, value, timeout));
    }

    /** 仅当 value 仍属于当前持有者时释放分布式锁。 */
    public boolean deleteIfValue(String key, String expectedValue) {
        Long result = redisTemplate.execute(RedisScript.of(
                        "if redis.call('get', KEYS[1]) == ARGV[1] then "
                                + "return redis.call('del', KEYS[1]) else return 0 end", Long.class),
                Collections.singletonList(key), expectedValue);
        return Long.valueOf(1L).equals(result);
    }

    /** 自增计数（配合 expire 做限流） */
    public long increment(String key, Duration timeout) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, timeout);
        }
        return count == null ? 0 : count;
    }

    // ---------- hash ----------

    public void hSet(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public void hDelete(String key, Object... fields) {
        redisTemplate.opsForHash().delete(key, fields);
    }

    // ---------- list ----------

    public void lPush(String key, String value) {
        redisTemplate.opsForList().leftPush(key, value);
    }

    public String rPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    public List<String> lRange(String key, long start, long end) {
        List<String> list = redisTemplate.opsForList().range(key, start, end);
        return list == null ? Collections.emptyList() : list;
    }

    // ---------- set ----------

    public void sAdd(String key, String... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    public Set<String> sMembers(String key) {
        Set<String> set = redisTemplate.opsForSet().members(key);
        return set == null ? Collections.emptySet() : set;
    }

    public boolean sIsMember(String key, String value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    // ---------- zset ----------

    public void zAdd(String key, String value, double score) {
        redisTemplate.opsForZSet().add(key, value, score);
    }

    public Set<String> zRange(String key, long start, long end) {
        Set<String> set = redisTemplate.opsForZSet().range(key, start, end);
        return set == null ? Collections.emptySet() : set;
    }

    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire == null ? -2 : expire;
    }
}
