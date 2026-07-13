package com.consultorprocessos.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "test"})
@Primary
@Slf4j
public class LogAdminEmailService implements AdminEmailService {

    @Override
    public void sendCourtRequestAlert(String courtName, String courtCode,
                                      String processNumber, long totalRequests) {
        log.info("[ADMIN EMAIL - DEV] SOLICITAÇÃO DE TRIBUNAL:" +
                 " tribunal='{}' código='{}' processo='{}' total={}",
                courtName, courtCode, processNumber, totalRequests);
    }

    @Override
    public void sendHealthScoreAlert(String courtCode, int score) {
        log.warn("[ADMIN EMAIL - DEV] HEALTH SCORE BAIXO: tribunal={} score={}", courtCode, score);
    }
}