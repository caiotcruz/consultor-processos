package com.consultorprocessos.court.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courts")
@Getter
@Setter
@NoArgsConstructor
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "code", nullable = false, length = 20, updatable = false, unique = true)
    private String code;

    @Column(name = "provider_class", nullable = false, length = 100)
    private String providerClass;

    @Column(name = "active", nullable = false)
    private boolean active = false;

    @Column(name = "rate_limit_per_min", nullable = false)
    private int rateLimitPerMin = 10;

    @Column(name = "min_delay_ms", nullable = false)
    private int minDelayMs = 1000;

    @Column(name = "max_delay_ms", nullable = false)
    private int maxDelayMs = 3000;

    @Column(name = "health_score", nullable = false)
    private int healthScore = 100;

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