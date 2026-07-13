package com.consultorprocessos.admin.service;

import com.consultorprocessos.court.event.CourtRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {

    private final AdminEmailService adminEmailService;

    @Value("${app.admin.court-request-alert-threshold:1}")
    private int courtRequestAlertThreshold;

    @Value("${app.admin.health-score-alert-threshold:50}")
    private int healthScoreAlertThreshold;

    @EventListener
    public void onCourtRequestCreated(CourtRequestCreatedEvent event) {
        log.info("[ADMIN] Nova solicitação de tribunal: '{}' ({}) | " +
                 "processo={} | total={}",
                event.getCourtName(), event.getCourtCode(),
                event.getProcessNumber(), event.getTotalRequests());

        if (event.getTotalRequests() >= courtRequestAlertThreshold) {
            adminEmailService.sendCourtRequestAlert(
                    event.getCourtName(),
                    event.getCourtCode(),
                    event.getProcessNumber(),
                    event.getTotalRequests()
            );
        }
    }

    public void onHealthScoreLow(String courtCode, int score) {
        if (score < healthScoreAlertThreshold) {
            log.warn("[ADMIN] Health score baixo: tribunal={} score={}", courtCode, score);
            adminEmailService.sendHealthScoreAlert(courtCode, score);
        }
    }
}