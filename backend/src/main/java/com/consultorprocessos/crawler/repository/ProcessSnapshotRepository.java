package com.consultorprocessos.crawler.repository;

import com.consultorprocessos.crawler.entity.ProcessSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessSnapshotRepository extends JpaRepository<ProcessSnapshot, UUID> {

    Optional<ProcessSnapshot> findFirstByProcessIdOrderByCapturedAtDesc(UUID processId);
}