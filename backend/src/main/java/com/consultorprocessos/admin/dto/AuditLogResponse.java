package com.consultorprocessos.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID    id,
        String  actorEmail,
        String  action,
        String  entityType,
        String  entityId,
        String  oldValue,
        String  newValue,
        String  ipAddress,
        Instant createdAt
) {}