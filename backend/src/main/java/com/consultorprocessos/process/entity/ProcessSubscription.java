package com.consultorprocessos.process.entity;

import com.consultorprocessos.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_subscriptions",
    uniqueConstraints = @UniqueConstraint(
        name = "process_subs_user_process_unique",
        columnNames = {"user_id", "process_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private Process process;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "alias", length = 200)
    private String alias;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public void deactivate() {
        this.active         = false;
        this.deactivatedAt  = Instant.now();
    }

    public void reactivate() {
        this.active         = true;
        this.deactivatedAt  = null;
    }
}