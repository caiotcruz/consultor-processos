package com.consultorprocessos.crawler.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MovimentacaoDetectadaEventLogger {

    @EventListener
    public void handle(MovimentacaoDetectadaEvent event) {
        log.info("[MOVIMENTAÇÃO DETECTADA] processId={} movimentos={}",
                event.getProcessId(),
                event.getSnapshot().movements().size());

        event.getSnapshot().movements().forEach(m ->
                log.info("  → {} | {}", m.date(), m.description()));
    }
}