package com.consultorprocessos.process.repository;

import com.consultorprocessos.process.entity.Process;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessRepository extends JpaRepository<Process, UUID> {

    Optional<Process> findByProcessNumberAndCourtId(String processNumber, UUID courtId);

    @Modifying
    @Query("UPDATE Process p SET p.lastCheckedAt = :ts WHERE p.id = :id")
    void updateLastChecked(@Param("id") UUID id,
                           @Param("ts") java.time.Instant ts);

    @Modifying
    @Query("UPDATE Process p SET p.consecutiveErrors = p.consecutiveErrors + 1 WHERE p.id = :id")
    void incrementConsecutiveErrors(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Process p SET p.consecutiveErrors = 0 WHERE p.id = :id")
    void resetConsecutiveErrors(@Param("id") UUID id);

    @Query(nativeQuery = true, value = """
        SELECT DISTINCT p.*
        FROM   processes p
        JOIN   courts c ON c.id = p.court_id
        WHERE  p.status IN ('PENDING', 'OK', 'ERROR')
        AND  c.active = true
        AND  EXISTS (
                SELECT 1
                FROM   process_subscriptions ps
                WHERE  ps.process_id = p.id
                    AND  ps.active = true
            )
        AND (
                p.last_checked_at IS NULL
                OR p.last_checked_at + (
                    INTERVAL '1 hour' * (
                        SELECT MIN(pl.check_interval_hours)
                        FROM   process_subscriptions ps2
                        JOIN   users u  ON u.id  = ps2.user_id
                        JOIN   plans pl ON pl.id = u.plan_id
                        WHERE  ps2.process_id = p.id
                            AND  ps2.active = true
                    )
                ) <= NOW()
            )
        ORDER BY p.last_checked_at NULLS FIRST
        LIMIT  :limit
        """)
    List<Process> findDueForChecking(@Param("limit") int limit);
}