package com.consultorprocessos.notification.repository;

import com.consultorprocessos.notification.entity.NotificationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {

    @EntityGraph(attributePaths = "process")
    Page<NotificationHistory> findByUserIdOrderBySentAtDesc(UUID userId, Pageable pageable);
}