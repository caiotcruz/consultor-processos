package com.consultorprocessos.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dlq_messages")
@Getter
@Setter
@NoArgsConstructor
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "process_id")
    private UUID processId;

    @Column(name = "process_number", nullable = false, length = 25)
    private String processNumber;

    @Column(name = "court_code", nullable = false, length = 20)
    private String courtCode;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processed_by", length = 255)
    private String processedBy;

    @PrePersist
    protected void onCreate() {
        queuedAt = Instant.now();
    }
}