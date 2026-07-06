package com.consultorprocessos.crawler.entity;

import com.consultorprocessos.court.entity.Court;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "court_health_scores")
@Getter
@Setter
@NoArgsConstructor
public class CourtHealthScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "success_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal successRate;

    @Column(name = "avg_duration_ms", nullable = false)
    private long avgDurationMs;

    @Column(name = "retry_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal retryRate;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;

    @PrePersist
    protected void onCreate() {
        calculatedAt = Instant.now();
    }
}