package com.consultorprocessos.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    @Value("${app.notifications.email.rate-limit-per-hour:10}")
    private int emailRateLimitPerHour;

    public boolean isAllowed(UUID userId, String channelCode) {
        int limit = "EMAIL".equals(channelCode) ? emailRateLimitPerHour : Integer.MAX_VALUE;
        String key = "notif:rate:" + userId + ":" + channelCode;

        try {
            Long current = redis.opsForValue().increment(key);
            if (current == null) return false;

            if (current == 1L) {
                redis.expire(key, WINDOW);
            }

            if (current > limit) {
                log.debug("Rate limit atingido: userId={} canal={} count={} limite={}",
                        userId, channelCode, current, limit);
                return false;
            }
            return true;

        } catch (Exception e) {
            log.warn("Redis indisponível para rate limit. Permitindo notificação. Erro: {}",
                    e.getMessage());
            return true;
        }
    }
}