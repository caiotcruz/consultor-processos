package com.consultorprocessos.notification.channel;

import com.consultorprocessos.crawler.model.Movement;

import java.util.List;
import java.util.UUID;

public record NotificationPayload(
        UUID         userId,
        UUID         processId,
        String       processNumber,
        String       courtName,
        String       userEmail,
        String       userName,
        List<String> deviceTokens,
        List<Movement> movements,
        String       unsubscribeUrl
) {}