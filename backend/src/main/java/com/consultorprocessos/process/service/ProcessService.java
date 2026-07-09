package com.consultorprocessos.process.service;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.repository.UserRepository;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.court.entity.CourtRequest;
import com.consultorprocessos.court.service.CourtRequestService;
import com.consultorprocessos.court.service.CourtService;
import com.consultorprocessos.plan.service.PlanService;
import com.consultorprocessos.process.dto.*;
import com.consultorprocessos.process.entity.*;
import com.consultorprocessos.process.entity.Process;
import com.consultorprocessos.process.exception.*;
import com.consultorprocessos.process.repository.*;
import com.consultorprocessos.shared.validation.ProcessNumberNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessService {

    private final ProcessRepository             processRepository;
    private final ProcessSubscriptionRepository subscriptionRepository;
    private final ProcessHistoryRepository      historyRepository;
    private final UserRepository                userRepository;
    private final CourtService                  courtService;
    private final CourtRequestService           courtRequestService;
    private final PlanService                   planService;
    private final ProcessNumberNormalizer       normalizer;

    @Transactional
    public SubscriptionResult subscribe(UserDetailsImpl principal,
                                        String rawNumber,
                                        String courtCode,
                                        String alias) {

        String normalized = normalizer.normalize(rawNumber);

        java.util.Optional<Court> courtOpt = courtService.findActiveByCode(courtCode);

        if (courtOpt.isEmpty()) {
            String courtName = courtService.resolveCourtName(courtCode);
            CourtRequest req = courtRequestService.register(
                principal.getUserId(), courtCode, courtName, normalized);
            return SubscriptionResult.courtUnavailable(req);
        }

        Court court = courtOpt.get();
        User  user  = loadUser(principal.getUserId());

        planService.assertHasCapacity(user);

        Process process = processRepository
            .findByProcessNumberAndCourtId(normalized, court.getId())
            .orElseGet(() -> {
                Process p = new Process();
                p.setProcessNumber(normalized);
                p.setProcessNumberRaw(rawNumber.strip());
                p.setCourt(court);
                p.setStatus(ProcessStatus.PENDING);
                return processRepository.save(p);
            });

        if (subscriptionRepository.existsByUserIdAndProcessId(
                user.getId(), process.getId())) {
            throw new SubscriptionAlreadyExistsException(normalized);
        }

        ProcessSubscription subscription = new ProcessSubscription();
        subscription.setUser(user);
        subscription.setProcess(process);
        subscription.setAlias(alias != null ? alias.strip() : null);
        subscriptionRepository.save(subscription);

        log.info("Processo cadastrado: userId={} processNumber={} tribunal={} processId={}",
            user.getId(), normalized, court.getCode(), process.getId());

        return SubscriptionResult.created(subscription);
    }

    @Transactional(readOnly = true)
    public Page<ProcessSummaryResponse> listByUser(UserDetailsImpl principal,
                                                Boolean active,
                                                ProcessStatus status,
                                                Pageable pageable) {
        Page<ProcessSubscription> page = subscriptionRepository
                .findByUserIdWithFilters(principal.getUserId(), active, status, pageable);

        List<UUID> processIds = page.getContent().stream()
                .map(sub -> sub.getProcess().getId())
                .toList();

        Map<UUID, String> lastMovementDescByProcess = loadLastMovementDescs(processIds);

        return page.map(sub -> toSummaryResponse(sub, lastMovementDescByProcess));
    }

    private Map<UUID, String> loadLastMovementDescs(List<UUID> processIds) {
        if (processIds.isEmpty()) {
            return Map.of();
        }
        return historyRepository.findLatestByProcessIds(processIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        h -> h.getProcess().getId(),
                        ProcessHistory::getDescription,
                        (a, b) -> a
                ));
    }

    @Transactional(readOnly = true)
    public ProcessSubscriptionResponse getById(UserDetailsImpl principal, UUID subscriptionId) {
        ProcessSubscription sub = findSubscription(principal.getUserId(), subscriptionId);
        return toDetailResponse(sub);
    }

    @Transactional
    public ProcessSubscriptionResponse updateAlias(UserDetailsImpl principal,
                                                   UUID subscriptionId,
                                                   String alias) {
        ProcessSubscription sub = findSubscription(principal.getUserId(), subscriptionId);
        sub.setAlias(alias != null ? alias.strip() : null);
        subscriptionRepository.save(sub);
        return toDetailResponse(sub);
    }

    @Transactional
    public ProcessSubscriptionResponse deactivate(UserDetailsImpl principal,
                                                  UUID subscriptionId) {
        ProcessSubscription sub = findSubscription(principal.getUserId(), subscriptionId);

        if (!sub.isActive()) {
            return toDetailResponse(sub);
        }

        sub.deactivate();
        subscriptionRepository.save(sub);

        log.info("Subscription desativada: subId={} userId={}",
            subscriptionId, principal.getUserId());

        return toDetailResponse(sub);
    }

    @Transactional
    public ProcessSubscriptionResponse reactivate(UserDetailsImpl principal,
                                                  UUID subscriptionId) {
        ProcessSubscription sub = findSubscription(principal.getUserId(), subscriptionId);

        if (sub.isActive()) {
            return toDetailResponse(sub);
        }
        User user = loadUser(principal.getUserId());
        planService.assertHasCapacity(user);

        sub.reactivate();
        subscriptionRepository.save(sub);

        log.info("Subscription reativada: subId={} userId={}",
            subscriptionId, principal.getUserId());

        return toDetailResponse(sub);
    }

    @Transactional
    public void delete(UserDetailsImpl principal, UUID subscriptionId) {
        ProcessSubscription sub = findSubscription(principal.getUserId(), subscriptionId);
        subscriptionRepository.delete(sub);

        log.info("Subscription removida: subId={} userId={} processId={}",
            subscriptionId, principal.getUserId(), sub.getProcess().getId());
    }

    @Transactional(readOnly = true)
    public Page<ProcessHistoryResponse> getHistory(UserDetailsImpl principal,
                                                   UUID subscriptionId,
                                                   Pageable pageable) {
        ProcessSubscription sub = findSubscription(principal.getUserId(), subscriptionId);

        return historyRepository
            .findByProcessIdOrderByDetectedAtDesc(sub.getProcess().getId(), pageable)
            .map(h -> new ProcessHistoryResponse(
                h.getId(),
                h.getDescription(),
                h.getMovementDate(),
                h.getDetectedAt()
            ));
    }

    private ProcessSubscription findSubscription(UUID userId, UUID subscriptionId) {
        return subscriptionRepository
            .findByIdAndUserId(subscriptionId, userId)
            .orElseThrow(SubscriptionNotFoundException::new);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException(
                "Usuário não encontrado: " + userId));
    }

    private ProcessSummaryResponse toSummaryResponse(ProcessSubscription sub,
                                                    Map<UUID, String> lastMovementDescs) {
        Process process = sub.getProcess();
        Court   court   = process.getCourt();

        String lastMovementDesc = lastMovementDescs.get(process.getId());

        return new ProcessSummaryResponse(
            sub.getId(),
            process.getId(),
            process.getProcessNumber(),
            sub.getAlias(),
            new ProcessSummaryResponse.CourtInfo(court.getCode(), court.getName()),
            process.getStatus().name(),
            sub.isActive(),
            process.getLastCheckedAt(),
            process.getLastMovementAt(),
            lastMovementDesc,
            sub.getCreatedAt()
        );
    }

    private ProcessSubscriptionResponse toDetailResponse(ProcessSubscription sub) {
        Process process = sub.getProcess();
        Court   court   = process.getCourt();

        return new ProcessSubscriptionResponse(
            sub.getId(),
            process.getId(),
            process.getProcessNumber(),
            sub.getAlias(),
            new ProcessSubscriptionResponse.CourtInfo(
                court.getCode(), court.getName(), court.getHealthScore()),
            process.getStatus().name(),
            sub.isActive(),
            process.getLastCheckedAt(),
            process.getLastMovementAt(),
            process.getConsecutiveErrors(),
            sub.getCreatedAt(),
            sub.getDeactivatedAt()
        );
    }

    @Transactional
    public void markAsSuccessful(UUID processId) {
        processRepository.findById(processId).ifPresent(p -> {
            p.setStatus(ProcessStatus.OK);
            p.setConsecutiveErrors(0);
            p.setLastCheckedAt(Instant.now());
            processRepository.save(p);
        });
    }

    @Transactional
    public void markAsBlocked(UUID processId) {
        processRepository.findById(processId).ifPresent(p -> {
            p.setStatus(ProcessStatus.BLOCKED);
            p.setConsecutiveErrors(p.getConsecutiveErrors() + 1);
            p.setLastCheckedAt(Instant.now());
            processRepository.save(p);
        });
    }

    @Transactional
    public void markAsError(UUID processId) {
        processRepository.findById(processId).ifPresent(p -> {
            p.setConsecutiveErrors(p.getConsecutiveErrors() + 1);
            p.setStatus(ProcessStatus.ERROR);
            p.setLastCheckedAt(Instant.now());
            processRepository.save(p);
        });
    }
}