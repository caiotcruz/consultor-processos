package com.consultorprocessos.crawler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionManager {

    private static final String KEY_PREFIX = "crawler:session:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    public Optional<Map<String, String>> getSession(String courtCode) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + courtCode);
            if (json == null) return Optional.empty();

            Map<String, String> cookies = objectMapper.readValue(
                    json, new TypeReference<>() {});
            return Optional.of(cookies);
        } catch (Exception e) {
            log.warn("Erro ao recuperar sessão do tribunal {}: {}", courtCode, e.getMessage());
            return Optional.empty();
        }
    }

    public void saveSession(String courtCode, Map<String, String> cookies) {
        saveSession(courtCode, cookies, DEFAULT_TTL);
    }

    public void saveSession(String courtCode, Map<String, String> cookies, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(cookies);
            redis.opsForValue().set(KEY_PREFIX + courtCode, json, ttl);
        } catch (Exception e) {
            log.warn("Erro ao salvar sessão do tribunal {}: {}", courtCode, e.getMessage());
        }
    }

    public void invalidateSession(String courtCode) {
        redis.delete(KEY_PREFIX + courtCode);
        log.debug("Sessão invalidada para tribunal {}.", courtCode);
    }
}