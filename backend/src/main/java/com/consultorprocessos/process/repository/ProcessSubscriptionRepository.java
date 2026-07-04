package com.consultorprocessos.process.repository;

import com.consultorprocessos.process.entity.ProcessStatus;
import com.consultorprocessos.process.entity.ProcessSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessSubscriptionRepository extends JpaRepository<ProcessSubscription, UUID> {

    boolean existsByUserIdAndProcessId(UUID userId, UUID processId);

    long countByUserIdAndActiveTrue(UUID userId);

    @Query("""
        SELECT ps FROM ProcessSubscription ps
        JOIN FETCH ps.process p
        JOIN FETCH p.court c
        WHERE ps.user.id = :userId
          AND (:active IS NULL OR ps.active = :active)
          AND (:status IS NULL OR p.status = :status)
        ORDER BY ps.createdAt DESC
        """)
    Page<ProcessSubscription> findByUserIdWithFilters(
            @Param("userId") UUID userId,
            @Param("active") Boolean active,
            @Param("status") ProcessStatus status,
            Pageable pageable);

    @Query("""
        SELECT ps FROM ProcessSubscription ps
        JOIN FETCH ps.process p
        JOIN FETCH p.court c
        WHERE ps.id = :id AND ps.user.id = :userId
        """)
    Optional<ProcessSubscription> findByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

 
    List<ProcessSubscription> findByProcessIdAndActiveTrue(UUID processId);

    @Modifying
    @Query("""
        UPDATE ProcessSubscription ps
        SET ps.active = false, ps.deactivatedAt = :now
        WHERE ps.user.id = :userId AND ps.active = true
        """)
    int deactivateAllByUserId(@Param("userId") UUID userId,
                              @Param("now") Instant now);
}