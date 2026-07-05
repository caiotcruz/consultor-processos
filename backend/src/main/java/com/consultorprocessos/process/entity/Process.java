package com.consultorprocessos.process.entity;

import com.consultorprocessos.court.entity.Court;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processes",
    uniqueConstraints = @UniqueConstraint(
        name = "processes_number_court_unique",
        columnNames = {"process_number", "court_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Process {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "process_number", nullable = false, length = 25)
    private String processNumber;

    @Column(name = "process_number_raw", nullable = false, length = 50)
    private String processNumberRaw;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProcessStatus status = ProcessStatus.PENDING;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;

    @Column(name = "last_snapshot_hash", length = 64)
    private String lastSnapshotHash;

    @Column(name = "consecutive_errors", nullable = false)
    private int consecutiveErrors = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}