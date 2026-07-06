package com.consultorprocessos.crawler.service;

import com.consultorprocessos.crawler.entity.CourtFeatureFlag;
import com.consultorprocessos.crawler.repository.CourtFeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {

    private static final Duration CACHE_TTL  = Duration.ofSeconds(30);
    private static final String   KEY_PREFIX = "flag:";

    private final CourtFeatureFlagRepository repository;
    private final StringRedisTemplate        redis;

    public boolean isEnabled(String courtCode, String flagKey) {
        String cacheKey = KEY_PREFIX + courtCode + ":" + flagKey;

        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return Boolean.parseBoolean(cached);
        }

        boolean value = repository.findByCourtCodeAndFlagKey(courtCode, flagKey)
                .map(CourtFeatureFlag::isEnabled)
                .orElse(false);

        redis.opsForValue().set(cacheKey, String.valueOf(value), CACHE_TTL);
        return value;
    }

    public void update(UUID courtId, String flagKey, boolean enabled, String updatedBy) {
        repository.findByCourtIdAndFlagKey(courtId, flagKey).ifPresent(flag -> {
            flag.setEnabled(enabled);
            flag.setUpdatedBy(updatedBy);
            repository.save(flag);
            redis.delete(KEY_PREFIX + "*:" + flagKey);
            log.info("Feature flag atualizada: court={} flag={} enabled={}", courtId, flagKey, enabled);
        });
    }

    public void invalidateCache(String courtCode) {
        String prefix = KEY_PREFIX + courtCode + ":";
        for (String knownFlag : java.util.List.of(
                "PLAYWRIGHT_ENABLED", "SELENIUM_ENABLED", "EXTRA_RETRY_ENABLED")) {
            redis.delete(prefix + knownFlag);
        }
    }
}