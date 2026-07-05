package com.consultorprocessos.court.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CourtRequestEventLogger {

    @EventListener
    public void handle(CourtRequestCreatedEvent event) {
        log.info("[COURT REQUEST] Tribunal: '{}' ({}) | Processo: {} | " +
                 "Total de solicitações: {}",
            event.getCourtName(),
            event.getCourtCode(),
            event.getProcessNumber(),
            event.getTotalRequests());
    }
}