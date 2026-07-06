package com.consultorprocessos.crawler.service;

import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.court.repository.CourtRepository;
import com.consultorprocessos.crawler.entity.CourtHealthScore;
import com.consultorprocessos.crawler.entity.CrawlerExecution;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.repository.CourtHealthScoreRepository;
import com.consultorprocessos.crawler.repository.CrawlerExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthScoreService {

    private static final int SAMPLE_SIZE = 100;

    private final CrawlerExecutionRepository executionRepository;
    private final CourtHealthScoreRepository healthScoreRepository;
    private final CourtRepository            courtRepository;

    @Transactional
    public void recalculate(UUID courtId) {
        Court court = courtRepository.findById(courtId).orElse(null);
        if (court == null) return;

        List<CrawlerExecution> executions =
                executionRepository.findLatestByCourt(courtId, SAMPLE_SIZE);

        if (executions.isEmpty()) {
            log.debug("Nenhuma execução encontrada para tribunal {}. Score mantido em 100.",
                    court.getCode());
            return;
        }

        long total      = executions.size();
        long successful = executions.stream().filter(CrawlerExecution::isSuccess).count();
        long fallbacks  = executions.stream()
                .filter(e -> e.getStrategy() != CrawlerStrategy.HTTP)
                .count();
        double avgDuration = executions.stream()
                .mapToLong(CrawlerExecution::getDurationMs)
                .average()
                .orElse(0.0);

        double successRate     = (double) successful / total;
        double retryRate       = (double) fallbacks / total;
        double speedScore      = Math.max(0, 100.0 - (avgDuration / 300.0));
        double stabilityScore  = Math.max(0, 100.0 - (retryRate * 100.0));
        int    score           = (int) Math.round(
                (successRate * 60) + (speedScore * 25) + (stabilityScore * 15));

        score = Math.max(0, Math.min(100, score));

        CourtHealthScore healthScore = new CourtHealthScore();
        healthScore.setCourt(court);
        healthScore.setScore(score);
        healthScore.setSuccessRate(BigDecimal.valueOf(successRate)
                .setScale(4, RoundingMode.HALF_UP));
        healthScore.setAvgDurationMs((long) avgDuration);
        healthScore.setRetryRate(BigDecimal.valueOf(retryRate)
                .setScale(4, RoundingMode.HALF_UP));
        healthScoreRepository.save(healthScore);

        court.setHealthScore(score);
        courtRepository.save(court);

        log.debug("HealthScore recalculado: court={} score={} successRate={:.0f}% avgDuration={}ms",
                court.getCode(), score, successRate * 100, (long) avgDuration);
    }
}