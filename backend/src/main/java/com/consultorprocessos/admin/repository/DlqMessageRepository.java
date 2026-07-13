// src/main/java/com/consultorprocessos/admin/repository/DlqMessageRepository.java
package com.consultorprocessos.admin.repository;

import com.consultorprocessos.admin.entity.DlqMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, UUID> {

    Page<DlqMessage> findByStatusOrderByQueuedAtDesc(String status, Pageable pageable);

    List<DlqMessage> findByStatus(String status);

    long countByStatus(String status);

    @Modifying
    @Query("UPDATE DlqMessage d SET d.status = :status, d.processedAt = :now, " +
           "d.processedBy = :actor WHERE d.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status,
                      @Param("now") Instant now, @Param("actor") String actor);
}