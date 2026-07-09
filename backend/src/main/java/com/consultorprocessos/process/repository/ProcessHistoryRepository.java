package com.consultorprocessos.process.repository;

import com.consultorprocessos.process.entity.ProcessHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessHistoryRepository extends JpaRepository<ProcessHistory, UUID> {

    Page<ProcessHistory> findByProcessIdOrderByDetectedAtDesc(UUID processId, Pageable pageable);

    Optional<ProcessHistory> findFirstByProcessIdOrderByDetectedAtDesc(UUID processId);

    @Query("""
        SELECT h FROM ProcessHistory h
        WHERE h.process.id IN :processIds
        AND h.detectedAt = (
            SELECT MAX(h2.detectedAt)
            FROM ProcessHistory h2
            WHERE h2.process.id = h.process.id
        )
        """)
    List<ProcessHistory> findLatestByProcessIds(
            @Param("processIds") List<UUID> processIds);
}