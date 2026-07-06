package com.consultorprocessos.crawler.entity;

import com.consultorprocessos.court.entity.Court;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parser_versions")
@Getter
@Setter
@NoArgsConstructor
public class ParserVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = false;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by", length = 255)
    private String releasedBy;
}