package com.consultorprocessos.court.event;

import org.springframework.context.ApplicationEvent;

public class CourtRequestCreatedEvent extends ApplicationEvent {

    private final String courtCode;
    private final String courtName;
    private final String processNumber;
    private final long   totalRequests;

    public CourtRequestCreatedEvent(Object source, String courtCode,
                                    String courtName, String processNumber,
                                    long totalRequests) {
        super(source);
        this.courtCode      = courtCode;
        this.courtName      = courtName;
        this.processNumber  = processNumber;
        this.totalRequests  = totalRequests;
    }

    public String getCourtCode()     { return courtCode; }
    public String getCourtName()     { return courtName; }
    public String getProcessNumber() { return processNumber; }
    public long   getTotalRequests() { return totalRequests; }
}