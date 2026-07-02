package com.consultorprocessos.shared.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redis;

    public boolean tryAcquire(String key, Duration ttl) {
        Boolean acquired = redis.opsForValue().setIfAbsent(key, "locked", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void release(String key) {
        redis.delete(key);
    }

    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }
}