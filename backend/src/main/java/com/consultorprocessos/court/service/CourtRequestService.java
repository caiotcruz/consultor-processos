package com.consultorprocessos.court.service;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.repository.UserRepository;
import com.consultorprocessos.court.entity.CourtRequest;
import com.consultorprocessos.court.event.CourtRequestCreatedEvent;
import com.consultorprocessos.court.repository.CourtRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourtRequestService {

    private final CourtRequestRepository courtRequestRepository;
    private final UserRepository         userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CourtRequest register(UUID userId, String courtCode,
                                 String courtName, String processNumber) {
        User userRef = userRepository.getReferenceById(userId);

        CourtRequest request = new CourtRequest();
        request.setUser(userRef);
        request.setCourtCode(courtCode != null ? courtCode.toUpperCase() : null);
        request.setCourtName(courtName);
        request.setProcessNumber(processNumber);
        CourtRequest saved = courtRequestRepository.save(request);

        long totalRequests = courtRequestRepository.countByCourtCode(
            request.getCourtCode());

        eventPublisher.publishEvent(new CourtRequestCreatedEvent(
            this,
            request.getCourtCode(),
            request.getCourtName(),
            processNumber,
            totalRequests
        ));

        log.info("CourtRequest criado: id={} tribunal={} totalSolicitacoes={}",
            saved.getId(), courtCode, totalRequests);

        return saved;
    }
}