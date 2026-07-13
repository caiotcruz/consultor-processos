package com.consultorprocessos.crawler.repository;

import com.consultorprocessos.crawler.entity.CourtFeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourtFeatureFlagRepository extends JpaRepository<CourtFeatureFlag, UUID> {

    Optional<CourtFeatureFlag> findByCourtCodeAndFlagKey(String courtCode, String flagKey);

    Optional<CourtFeatureFlag> findByCourtIdAndFlagKey(UUID courtId, String flagKey);

    List<CourtFeatureFlag> findByCourtId(UUID courtId);
}