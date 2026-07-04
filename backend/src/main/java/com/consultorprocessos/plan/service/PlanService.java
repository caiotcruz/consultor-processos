package com.consultorprocessos.plan.service;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.plan.entity.Plan;
import com.consultorprocessos.plan.repository.PlanRepository;
import com.consultorprocessos.process.repository.ProcessSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final ProcessSubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public int countActiveSubscriptions(UUID userId) {
        return (int) subscriptionRepository.countByUserIdAndActiveTrue(userId);
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