package com.consultorprocessos.process.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProcessHistoryResponse(
        UUID      id,
        String    description,
        LocalDate movementDate,
        Instant   detectedAt
) {}