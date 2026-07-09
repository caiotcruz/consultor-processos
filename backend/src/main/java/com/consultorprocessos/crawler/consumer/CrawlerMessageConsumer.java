package com.consultorprocessos.crawler.consumer;

import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.court.repository.CourtRepository;
import com.consultorprocessos.crawler.entity.CrawlerExecution;
import com.consultorprocessos.crawler.entity.ParserVersion;
import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.provider.CourtProviderFactory;
import com.consultorprocessos.crawler.repository.CrawlerExecutionRepository;
import com.consultorprocessos.crawler.repository.ParserVersionRepository;
import com.consultorprocessos.crawler.service.CrawlerExecutionRecorder;
import com.consultorprocessos.crawler.service.HealthScoreService;
import com.consultorprocessos.crawler.service.SnapshotComparator;
import com.consultorprocessos.process.entity.Process;
import com.consultorprocessos.process.repository.ProcessRepository;
import com.consultorprocessos.process.service.ProcessService;
import com.consultorprocessos.shared.config.RabbitConfig;
import com.consultorprocessos.scheduler.model.CrawlRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlerMessageConsumer {

    private static final int     MAX_RETRIES       = 3;
    private static final long[]  RETRY_DELAYS_MS   = {
            5L  * 60 * 1000,
            30L * 60 * 1000,
            120L * 60 * 1000 
    };

    private final CourtProviderFactory   providerFactory;
    private final SnapshotComparator     snapshotComparator;
    private final CrawlerExecutionRecorder recorder;
    private final HealthScoreService     healthScoreService;
    private final ProcessRepository      processRepository;
    private final CourtRepository        courtRepository;
    private final ParserVersionRepository parserVersionRepository;
    private final ProcessService         processService;
    private final RabbitTemplate         rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.QUEUE_CRAWL_REQUESTS)
    public void handle(@Payload CrawlRequestMessage message) {
        log.debug("Consumer: recebida mensagem processo={} tribunal={} retry={}",
                message.processNumber(), message.courtCode(), message.retryCount());

        Process process = processRepository.findById(message.processId()).orElse(null);
        if (process == null) {
            log.warn("Consumer: processo {} não encontrado no banco. Mensagem descartada.",
                    message.processId());
            return;
        }

        long startTime = System.currentTimeMillis();
        CrawlerSnapshot snapshot = null;

        try {
            CourtProvider provider = providerFactory.getProvider(message.courtCode());
            snapshot = provider.consultar(message.processNumber());
        } catch (CourtBlockedException e) {
            log.warn("Consumer: acesso bloqueado pelo tribunal. processo={} motivo={}",
                    message.processNumber(), e.getMessage());
            handleBlock(process, message, e, startTime);
            return;
        } catch (Exception e) {
            log.warn("Consumer: falha na consulta. processo={} erro={}",
                    message.processNumber(), e.getMessage());
            handleRetryOrDlq(process, message, e, startTime);
            return;
        }

        handleSuccess(process, message, snapshot, startTime);
    }

    private void handleSuccess(Process process, CrawlRequestMessage msg,
                                CrawlerSnapshot snapshot, long startTime) {
        try {
            snapshotComparator.compareAndPersist(process.getId(), snapshot);

            processService.markAsSuccessful(process.getId());

            recordExecution(process, msg, snapshot.strategyUsed(),
                    true, startTime, null, null, null, msg.courtCode());

            healthScoreService.recalculate(msg.courtId());

            log.info("Consumer: sucesso. processo={} tribunal={} movimentos={}",
                    msg.processNumber(), msg.courtCode(), snapshot.movements().size());

        } catch (Exception e) {
            log.error("Consumer: erro no pós-processamento após crawl bem-sucedido. " +
                      "processo={} erro={}", msg.processNumber(), e.getMessage(), e);
        }
    }

    private void handleBlock(Process process, CrawlRequestMessage msg,
                              CourtBlockedException e, long startTime) {
        try {
            processService.markAsBlocked(process.getId());

            recordExecution(process, msg, CrawlerStrategy.HTTP,
                    false, startTime, null, "BLOCKED", e.getMessage(), msg.courtCode());

            healthScoreService.recalculate(msg.courtId());

        } catch (Exception ex) {
            log.error("Consumer: erro ao registrar bloqueio. processo={} erro={}",
                    msg.processNumber(), ex.getMessage(), ex);
        }
    }

    private void handleRetryOrDlq(Process process, CrawlRequestMessage msg,
                                   Exception e, long startTime) {
        try {
            recordExecution(process, msg, CrawlerStrategy.HTTP,
                    false, startTime, null,
                    e.getClass().getSimpleName(), e.getMessage(), msg.courtCode());

            healthScoreService.recalculate(msg.courtId());
        } catch (Exception ex) {
            log.error("Consumer: erro ao registrar falha. processo={}", msg.processNumber(), ex);
        }

        if (msg.retryCount() < MAX_RETRIES) {
            publishRetry(msg, e);
        } else {
            publishDlq(process, msg, e);
        }
    }

    private void publishRetry(CrawlRequestMessage msg, Exception e) {
        long ttlMs = RETRY_DELAYS_MS[msg.retryCount()];
        long ttlMin = ttlMs / 60_000;

        CrawlRequestMessage retry = new CrawlRequestMessage(
                msg.processId(), msg.courtId(),
                msg.processNumber(), msg.courtCode(),
                msg.retryCount() + 1
        );

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL,
                RabbitConfig.ROUTING_KEY_CRAWL_RETRY,
                retry,
                m -> {
                    m.getMessageProperties().setExpiration(String.valueOf(ttlMs));
                    return m;
                }
        );

        log.warn("Consumer: retry {}/{} agendado em {}min. processo={} erro={}",
                msg.retryCount() + 1, MAX_RETRIES, ttlMin,
                msg.processNumber(), e.getMessage());
    }

    private void publishDlq(Process process, CrawlRequestMessage msg, Exception e) {
        try {
            processService.markAsError(process.getId());
        } catch (Exception ex) {
            log.error("Consumer: falha ao marcar processo como ERROR. id={}", process.getId(), ex);
        }

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL_DLX,
                "crawl.dead",
                msg
        );

        log.error("Consumer: máximo de retries atingido ({}). processo={} tribunal={} erro={}. " +
                  "Mensagem enviada para crawl.dlq.",
                MAX_RETRIES, msg.processNumber(), msg.courtCode(), e.getMessage());
    }

    private void recordExecution(Process process, CrawlRequestMessage msg,
                                  CrawlerStrategy strategy, boolean success,
                                  long startTime, Integer httpStatusCode,
                                  String errorType, String errorMessage,
                                  String courtCode) {
        try {
            Court court = courtRepository.findById(msg.courtId()).orElse(null);
            if (court == null) return;

            ParserVersion pv = parserVersionRepository
                    .findByCourtCodeAndActiveTrue(courtCode)
                    .orElse(null);

            recorder.record(
                    process, court, strategy, success,
                    System.currentTimeMillis() - startTime,
                    httpStatusCode, errorType, errorMessage, pv
            );
        } catch (Exception e) {
            log.error("Consumer: falha ao gravar CrawlerExecution. processo={} erro={}",
                    msg.processNumber(), e.getMessage());
        }
    }
}