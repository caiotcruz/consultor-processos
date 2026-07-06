package com.consultorprocessos.crawler.entity;

import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.process.entity.Process;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class ProcessSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false, updatable = false)
    private Process process;

    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Column(name = "raw_content", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String rawContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parser_version_id", nullable = false, updatable = false)
    private ParserVersion parserVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "crawler_strategy", nullable = false, length = 20, updatable = false)
    private CrawlerStrategy crawlerStrategy;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @PrePersist
    protected void onCreate() {
        capturedAt = Instant.now();
    }
}