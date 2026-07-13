package com.consultorprocessos.court.repository;

import com.consultorprocessos.court.entity.CourtRequest;
import com.consultorprocessos.court.entity.CourtRequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CourtRequestRepository extends JpaRepository<CourtRequest, UUID> {

    long countByCourtCode(String courtCode);

    Page<CourtRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<CourtRequest> findByStatusOrderByCreatedAtDesc(
            CourtRequestStatus status, Pageable pageable);
}