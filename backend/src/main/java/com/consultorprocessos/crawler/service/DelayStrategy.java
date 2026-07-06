package com.consultorprocessos.crawler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class DelayStrategy {

    public void apply(int minDelayMs, int maxDelayMs) {
        if (minDelayMs <= 0) return;

        long delay = minDelayMs == maxDelayMs
                ? minDelayMs
                : ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1L);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("DelayStrategy interrompido durante sleep de {}ms.", delay);
        }
    }
}