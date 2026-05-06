package com.danmaku.config;

import com.danmaku.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RedisConfigTest {

    @Test
    void redisSerializerShouldRoundTripUserInfoVoWithJavaTimeFields() {
        RedisConfig redisConfig = new RedisConfig();
        GenericJackson2JsonRedisSerializer serializer = redisConfig.redisValueSerializer();

        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setId("1");
        userInfoVO.setUsername("tester");
        userInfoVO.setAvatarUrl("http://localhost/avatar.jpg");
        userInfoVO.setCreatedAt(LocalDateTime.of(2026, 3, 25, 20, 30, 0));
        userInfoVO.setUpdatedAt(LocalDateTime.of(2026, 3, 25, 21, 0, 0));

        byte[] payload = serializer.serialize(userInfoVO);
        assertNotNull(payload);

        Object restored = serializer.deserialize(payload);
        UserInfoVO restoredUserInfo = assertInstanceOf(UserInfoVO.class, restored);
        assertEquals("1", restoredUserInfo.getId());
        assertEquals("tester", restoredUserInfo.getUsername());
        assertEquals("http://localhost/avatar.jpg", restoredUserInfo.getAvatarUrl());
        assertEquals(LocalDateTime.of(2026, 3, 25, 20, 30, 0), restoredUserInfo.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 3, 25, 21, 0, 0), restoredUserInfo.getUpdatedAt());
    }
}
