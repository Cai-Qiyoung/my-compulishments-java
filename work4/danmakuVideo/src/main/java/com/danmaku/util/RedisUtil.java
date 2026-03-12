package com.danmaku.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

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
}