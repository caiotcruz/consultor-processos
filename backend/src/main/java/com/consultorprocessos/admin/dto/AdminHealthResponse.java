package com.consultorprocessos.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminHealthResponse(
        String                  overallStatus,
        Instant                 timestamp,
        Map<String,String>      infrastructure,
        List<CourtHealth>       courts,
        SchedulerInfo           scheduler,
        QueueInfo               queues
) {
    public record CourtHealth(
            String code, String name, int healthScore, boolean active) {}

    public record SchedulerInfo(
            boolean enabled, long pendingProcesses) {}

    public record QueueInfo(
            long crawlRequests, long crawlRetry, long dlqPending) {}
}