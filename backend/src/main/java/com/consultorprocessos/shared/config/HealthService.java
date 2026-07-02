package com.consultorprocessos.shared.config;

import com.consultorprocessos.shared.config.HealthController.HealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthService {

    private final JdbcTemplate         jdbcTemplate;
    private final StringRedisTemplate  redisTemplate;
    private final RabbitTemplate       rabbitTemplate;

    public HealthResponse check() {
        Map<String, String> components = new LinkedHashMap<>();

        components.put("database", checkDatabase());
        components.put("redis",    checkRedis());
        components.put("rabbitmq", checkRabbitMQ());

        boolean allUp = components.values().stream().allMatch("UP"::equals);
        String  status = allUp ? "UP" : "DOWN";

        if (!allUp) {
            log.warn("Health check DEGRADADO. Componentes: {}", components);
        }

        return new HealthResponse(status, Instant.now(), components);
    }

    private String checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.error("Health check — banco de dados DOWN: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.error("Health check — Redis DOWN: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkRabbitMQ() {
        try {
            rabbitTemplate.execute(channel -> {
                channel.basicQos(1);
                return null;
            });
            return "UP";
        } catch (Exception e) {
            log.error("Health check — RabbitMQ DOWN: {}", e.getMessage());
            return "DOWN";
        }
    }
}