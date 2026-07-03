package com.consultorprocessos.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID                  id,
        String                name,
        String                email,
        String                status,
        PlanInfo              plan,
        UsageInfo             usage,
        NotificationInfo      notifications,
        Instant               createdAt,
        Instant               lastLoginAt
) {
    public record PlanInfo(
            String  name,
            String  displayName,
            Integer maxProcesses,
            int     checkIntervalHours
    ) {}

    public record UsageInfo(
            int     activeProcesses,
            Integer remainingProcesses
    ) {}

    public record NotificationInfo(
            boolean emailEnabled,
            boolean pushEnabled
    ) {}
}