// src/main/java/com/consultorprocessos/plan/service/PlanService.java
package com.consultorprocessos.plan.service;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.plan.entity.Plan;
import com.consultorprocessos.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final JdbcTemplate   jdbcTemplate;

    @Transactional(readOnly = true)
    public int countActiveSubscriptions(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM process_subscriptions " +
            "WHERE user_id = ? AND active = true",
            Integer.class,
            userId
        );
        return count != null ? count : 0;
    }

    @Transactional(readOnly = true)
    public boolean hasCapacity(User user) {
        Integer maxProcesses = user.getPlan().getMaxProcesses();
        if (maxProcesses == null) {
            return true;
        }
        int active = countActiveSubscriptions(user.getId());
        return active < maxProcesses;
    }

    @Transactional(readOnly = true)
    public void assertHasCapacity(User user) {
        if (!hasCapacity(user)) {
            Integer max = user.getPlan().getMaxProcesses();
            throw new ProcessLimitReachedException(max);
        }
    }

    @Transactional(readOnly = true)
    public Integer getRemainingCapacity(User user) {
        Integer maxProcesses = user.getPlan().getMaxProcesses();
        if (maxProcesses == null) {
            return null;
        }
        int active = countActiveSubscriptions(user.getId());
        return Math.max(0, maxProcesses - active);
    }
}