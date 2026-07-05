package com.consultorprocessos.court.repository;

import com.consultorprocessos.court.entity.CourtRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourtRequestRepository extends JpaRepository<CourtRequest, UUID> {

    long countByCourtCode(String courtCode);
}