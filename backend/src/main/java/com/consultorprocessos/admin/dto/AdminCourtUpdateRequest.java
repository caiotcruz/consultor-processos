package com.consultorprocessos.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminCourtUpdateRequest(
        Boolean active,

        @Min(value = 1, message = "Rate limit mínimo: 1 req/min")
        @Max(value = 120, message = "Rate limit máximo: 120 req/min")
        Integer rateLimitPerMin,

        @Min(0) Integer minDelayMs,
        @Min(0) Integer maxDelayMs
) {}