package com.consultorprocessos.crawler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class CourtRateLimiter {

    private final ConcurrentHashMap<String, AtomicLong> lastRequestTimes =
            new ConcurrentHashMap<>();

    public void acquire(String courtCode, int rateLimitPerMin) {
        if (rateLimitPerMin <= 0) return;

        long minIntervalMs = 60_000L / rateLimitPerMin;
        AtomicLong lastTime = lastRequestTimes
                .computeIfAbsent(courtCode, k -> new AtomicLong(0L));

        synchronized (lastTime) {
            long now     = System.currentTimeMillis();
            long last    = lastTime.get();
            long elapsed = now - last;

            if (elapsed < minIntervalMs) {
                long wait = minIntervalMs - elapsed;
                log.debug("RateLimiter: aguardando {}ms para tribunal {}.", wait, courtCode);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastTime.set(System.currentTimeMillis());
        }
    }
}