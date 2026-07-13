package com.consultorprocessos.admin.dto;

import java.util.Map;
import java.util.UUID;

public record AdminCourtResponse(
        UUID              id,
        String            name,
        String            code,
        String            providerClass,
        boolean           active,
        int               healthScore,
        int               rateLimitPerMin,
        int               minDelayMs,
        int               maxDelayMs,
        Map<String,Boolean> featureFlags
) {}