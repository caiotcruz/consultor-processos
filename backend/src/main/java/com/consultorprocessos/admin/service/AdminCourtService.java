package com.consultorprocessos.admin.service;

import com.consultorprocessos.admin.annotation.Audited;
import com.consultorprocessos.admin.dto.AdminCourtResponse;
import com.consultorprocessos.admin.dto.AdminCourtUpdateRequest;
import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.court.entity.CourtRequest;
import com.consultorprocessos.court.entity.CourtRequestStatus;
import com.consultorprocessos.court.repository.CourtRepository;
import com.consultorprocessos.court.repository.CourtRequestRepository;
import com.consultorprocessos.crawler.entity.CourtFeatureFlag;
import com.consultorprocessos.crawler.repository.CourtFeatureFlagRepository;
import com.consultorprocessos.crawler.service.FeatureFlagService;
import com.consultorprocessos.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCourtService {

    private final CourtRepository           courtRepository;
    private final CourtRequestRepository    courtRequestRepository;
    private final CourtFeatureFlagRepository flagRepository;
    private final FeatureFlagService        featureFlagService;

    @Transactional(readOnly = true)
    public List<AdminCourtResponse> listAllCourts() {
        return courtRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminCourtResponse getCourtByCode(String code) {
        return courtRepository.findByCode(code.toUpperCase())
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Tribunal não encontrado: " + code));
    }

    @Audited(action = "UPDATE_COURT", entityType = "COURT")
    @Transactional
    public AdminCourtResponse updateCourt(String code, AdminCourtUpdateRequest request) {
        Court court = courtRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Tribunal não encontrado: " + code));

        if (request.active()         != null) court.setActive(request.active());
        if (request.rateLimitPerMin() != null) court.setRateLimitPerMin(request.rateLimitPerMin());
        if (request.minDelayMs()     != null) court.setMinDelayMs(request.minDelayMs());
        if (request.maxDelayMs()     != null) court.setMaxDelayMs(request.maxDelayMs());

        courtRepository.save(court);
        log.info("Admin: tribunal {} atualizado: active={} rateLimit={}",
                code, court.isActive(), court.getRateLimitPerMin());
        return toResponse(court);
    }

    @Audited(action = "UPDATE_FEATURE_FLAG", entityType = "COURT_FLAG")
    @Transactional
    public void updateFeatureFlag(UUID courtId, String flagKey, boolean enabled,
                                  String updatedBy) {
        featureFlagService.update(courtId, flagKey, enabled, updatedBy);
        log.info("Admin: feature flag atualizada: courtId={} flag={} enabled={}",
                courtId, flagKey, enabled);
    }

    private AdminCourtResponse toResponse(Court court) {
        Map<String, Boolean> flags = flagRepository.findByCourtId(court.getId()).stream()
                .collect(Collectors.toMap(
                        CourtFeatureFlag::getFlagKey,
                        CourtFeatureFlag::isEnabled
                ));
        return new AdminCourtResponse(
                court.getId(), court.getName(), court.getCode(),
                court.getProviderClass(), court.isActive(), court.getHealthScore(),
                court.getRateLimitPerMin(), court.getMinDelayMs(), court.getMaxDelayMs(),
                flags
        );
    }
}