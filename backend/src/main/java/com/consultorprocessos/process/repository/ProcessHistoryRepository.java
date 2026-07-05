package com.consultorprocessos.process.repository;

import com.consultorprocessos.process.entity.ProcessHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessHistoryRepository extends JpaRepository<ProcessHistory, UUID> {

    Page<ProcessHistory> findByProcessIdOrderByDetectedAtDesc(UUID processId, Pageable pageable);

    Optional<ProcessHistory> findFirstByProcessIdOrderByDetectedAtDesc(UUID processId);
}