package com.consultorprocessos.court.service;

import com.consultorprocessos.court.dto.CourtResponse;
import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.court.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtService {

    private final CourtRepository courtRepository;

    @Transactional(readOnly = true)
    public List<CourtResponse> listActive() {
        return courtRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CourtResponse getByCode(String code) {
        return courtRepository.findByCode(code.toUpperCase())
                .map(this::toResponse)
                .orElseThrow(() -> new com.consultorprocessos.shared.exception
                        .NotFoundException("Tribunal '" + code + "' não encontrado."));
    }

    @Transactional(readOnly = true)
    public Optional<Court> findActiveByCode(String code) {
        return courtRepository.findByCode(code.toUpperCase())
                .filter(Court::isActive);
    }

    @Transactional(readOnly = true)
    public String resolveCourtName(String courtCode) {
        return courtRepository.findByCode(courtCode.toUpperCase())
                .map(Court::getName)
                .orElse(courtCode);
    }

    private CourtResponse toResponse(Court court) {
        return new CourtResponse(
            court.getId(),
            court.getName(),
            court.getCode(),
            court.isActive(),
            court.getHealthScore()
        );
    }
}