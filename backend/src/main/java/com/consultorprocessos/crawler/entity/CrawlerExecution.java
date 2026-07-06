package com.consultorprocessos.crawler.entity;

import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.process.entity.Process;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crawler_executions")
@Getter
@Setter
@NoArgsConstructor
public class CrawlerExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private Process process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 20)
    private CrawlerStrategy strategy;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "error_type", length = 50)
    private String errorType;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parser_version_id")
    private ParserVersion parserVersion;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        executedAt = Instant.now();
    }
}