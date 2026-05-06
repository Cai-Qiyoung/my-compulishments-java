package com.danmaku.util;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    public org.springframework.data.redis.core.ZSetOperations<String, Object> opsForZSet() {
        return redisTemplate.opsForZSet();
    }

    @SuppressWarnings("unchecked")
    public <T> T getCacheObject(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getCacheObjectSafely(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            deleteObject(key);
            return null;
        }
    }

    public void setCacheObject(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public boolean setCacheObjectSafely(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean deleteObject(String key) {
        return redisTemplate.delete(key);
    }

    public Long deleteObjects(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return redisTemplate.delete(keys);
    }

    public Long deleteByPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        return deleteObjects(keys);
    }

    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    public void expire(String key, long timeout, TimeUnit unit) {
        redisTemplate.expire(key, timeout, unit);
    }

    public <T> long rightPush(String key, T value) {
        Long size = redisTemplate.opsForList().rightPush(key, value);
        return size == null ? 0L : size;
    }

    public void trim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getListRange(String key, long start, long end) {
        return (List<T>) (List<?>) redisTemplate.opsForList().range(key, start, end);
    }

    public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        return Boolean.TRUE.equals(success);
    }

    public boolean unlock(String key, String value) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) else return 0 end"
        );
        script.setResultType(Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), value);
        return Long.valueOf(1L).equals(result);
    }

    public void deleteByPrefixAsync(String prefix) {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            return null;
        });
    }
}
