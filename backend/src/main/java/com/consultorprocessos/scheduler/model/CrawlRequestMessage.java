package com.consultorprocessos.scheduler.model;

import java.util.UUID;

public record CrawlRequestMessage(
        UUID   processId,
        UUID   courtId,
        String processNumber,
        String courtCode,
        int    retryCount
) {}