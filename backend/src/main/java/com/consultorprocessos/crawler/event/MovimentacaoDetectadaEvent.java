package com.consultorprocessos.crawler.event;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class MovimentacaoDetectadaEvent extends ApplicationEvent {

    private final UUID            processId;
    private final CrawlerSnapshot snapshot;

    public MovimentacaoDetectadaEvent(Object source, UUID processId,
                                      CrawlerSnapshot snapshot) {
        super(source);
        this.processId = processId;
        this.snapshot  = snapshot;
    }

    public UUID            getProcessId() { return processId; }
    public CrawlerSnapshot getSnapshot()  { return snapshot; }
}