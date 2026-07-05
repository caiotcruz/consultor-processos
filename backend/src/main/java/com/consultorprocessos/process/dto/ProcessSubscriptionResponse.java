package com.consultorprocessos.process.dto;

import java.time.Instant;
import java.util.UUID;

public record ProcessSubscriptionResponse(
        UUID      subscriptionId,
        UUID      processId,
        String    processNumber,
        String    alias,
        CourtInfo court,
        String    status,
        boolean   active,
        Instant   lastCheckedAt,
        Instant   lastMovementAt,
        int       consecutiveErrors,
        Instant   createdAt,
        Instant   deactivatedAt
) {
    public record CourtInfo(String code, String name, int healthScore) {}
}