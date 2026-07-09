package com.consultorprocessos.scheduler.service;

import com.consultorprocessos.process.entity.Process;
import com.consultorprocessos.process.repository.ProcessRepository;
import com.consultorprocessos.scheduler.model.CrawlRequestMessage;
import com.consultorprocessos.shared.config.RabbitConfig;
import com.consultorprocessos.shared.config.RedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private static final int    BATCH_LIMIT      = 200;
    private static final String LOCK_KEY         = "scheduler:lock:main";

    private final ProcessRepository  processRepository;
    private final RabbitTemplate     rabbitTemplate;
    private final RedisLockService   redisLockService;

    @Value("${app.scheduler.lock-ttl-seconds:540}")
    private int lockTtlSeconds;
    
    @Transactional(readOnly = true)
    @Scheduled(cron = "${app.scheduler.cron:0 */10 * * * *}")
    public void scheduleAll() {
        Duration lockTtl = Duration.ofSeconds(lockTtlSeconds);

        if (!redisLockService.tryAcquire(LOCK_KEY, lockTtl)) {
            log.debug("Scheduler: lock não adquirido — outra instância em execução.");
            return;
        }

        try {
            List<Process> dueProcesses = processRepository.findDueForChecking(BATCH_LIMIT);

            if (dueProcesses.isEmpty()) {
                log.debug("Scheduler: nenhum processo pendente de consulta.");
                return;
            }

            int published = 0;
            for (Process process : dueProcesses) {
                try {
                    publishCrawlRequest(process);
                    published++;
                } catch (Exception e) {
                    log.error("Scheduler: falha ao publicar mensagem para processo {}: {}",
                            process.getProcessNumber(), e.getMessage());
                }
            }

            log.info("Scheduler: {} processos agendados para consulta (lote de {}).",
                    published, BATCH_LIMIT);

        } finally {
            redisLockService.release(LOCK_KEY);
        }
    }

    private void publishCrawlRequest(Process process) {
        CrawlRequestMessage message = new CrawlRequestMessage(
                process.getId(),
                process.getCourt().getId(),
                process.getProcessNumber(),
                process.getCourt().getCode(),
                0
        );

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL,
                RabbitConfig.ROUTING_KEY_CRAWL_REQUEST,
                message
        );

        log.debug("Crawler agendado: processo={} tribunal={}",
                process.getProcessNumber(), process.getCourt().getCode());
    }
}