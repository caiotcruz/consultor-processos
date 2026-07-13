package com.consultorprocessos.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID    id,
        String  name,
        String  email,
        String  status,
        String  plan,
        int     activeProcesses,
        Instant createdAt,
        Instant lastLoginAt
) {}