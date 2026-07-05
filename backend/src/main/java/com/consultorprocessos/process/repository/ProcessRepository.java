package com.consultorprocessos.process.repository;

import com.consultorprocessos.process.entity.Process;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}