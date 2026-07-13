package com.consultorprocessos.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCourtRequestResponse(
        UUID    id,
        String  courtName,
        String  courtCode,
        String  processNumber,
        String  status,
        String  adminNotes,
        String  requesterEmail, 
        Instant createdAt
) {}