package com.consultorprocessos.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record DlqMessageResponse(
        UUID    id,
        String  processNumber,
        String  courtCode,
        int     retryCount,
        String  errorMessage,
        String  status,
        Instant queuedAt
) {}