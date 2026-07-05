package com.consultorprocessos.process.dto;

import com.consultorprocessos.court.entity.CourtRequest;
import com.consultorprocessos.process.entity.ProcessSubscription;

import java.util.UUID;

public record SubscriptionResult(
        ResultType          type,
        ProcessSubscription subscription,
        UUID                courtRequestId,
        String              courtName
) {
    public enum ResultType { CREATED, COURT_UNAVAILABLE }

    public static SubscriptionResult created(ProcessSubscription sub) {
        return new SubscriptionResult(ResultType.CREATED, sub, null, null);
    }

    public static SubscriptionResult courtUnavailable(CourtRequest req) {
        return new SubscriptionResult(
            ResultType.COURT_UNAVAILABLE, null,
            req.getId(), req.getCourtName());
    }

    public boolean isCreated() {
        return type == ResultType.CREATED;
    }
}