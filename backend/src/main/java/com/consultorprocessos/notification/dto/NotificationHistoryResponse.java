package com.consultorprocessos.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationHistoryResponse(
        UUID    id,
        String  channel,
        String  eventType,
        String  status,
        String  errorMessage,
        Instant sentAt,
        String  processNumber
) {}