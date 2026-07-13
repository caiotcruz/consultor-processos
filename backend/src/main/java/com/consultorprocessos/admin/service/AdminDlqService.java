package com.consultorprocessos.admin.service;

import com.consultorprocessos.admin.annotation.Audited;
import com.consultorprocessos.admin.dto.DlqMessageResponse;
import com.consultorprocessos.admin.entity.DlqMessage;
import com.consultorprocessos.admin.repository.DlqMessageRepository;
import com.consultorprocessos.scheduler.model.CrawlRequestMessage;
import com.consultorprocessos.shared.config.RabbitConfig;
import com.consultorprocessos.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDlqService {

    private final DlqMessageRepository dlqMessageRepository;
    private final RabbitTemplate        rabbitTemplate;

    @Transactional(readOnly = true)
    public Page<DlqMessageResponse> listPending(Pageable pageable) {
        return dlqMessageRepository.findByStatusOrderByQueuedAtDesc("PENDING", pageable)
                .map(this::toResponse);
    }

    @Audited(action = "REQUEUE_DLQ_MESSAGE", entityType = "DLQ_MESSAGE")
    @Transactional
    public void requeue(UUID dlqMessageId, String actorEmail) {
        DlqMessage msg = dlqMessageRepository.findById(dlqMessageId)
                .orElseThrow(() -> new NotFoundException("Mensagem DLQ não encontrada."));

        if (!"PENDING".equals(msg.getStatus())) {
            throw new com.consultorprocessos.shared.exception.DomainException(
                    "DLQ_MESSAGE_ALREADY_PROCESSED",
                    "Mensagem já foi processada (status: " + msg.getStatus() + ").",
                    org.springframework.http.HttpStatus.CONFLICT) {};
        }

        CrawlRequestMessage crawlMsg = new CrawlRequestMessage(
                msg.getProcessId(),
                null,   
                msg.getProcessNumber(),
                msg.getCourtCode(),
                0
        );

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL,
                RabbitConfig.ROUTING_KEY_CRAWL_REQUEST,
                crawlMsg
        );

        dlqMessageRepository.updateStatus(dlqMessageId, "REQUEUED", Instant.now(), actorEmail);

        log.info("Admin: DLQ message requeueada: id={} processo={}",
                dlqMessageId, msg.getProcessNumber());
    }

    @Audited(action = "DISCARD_DLQ_MESSAGE", entityType = "DLQ_MESSAGE")
    @Transactional
    public void discard(UUID dlqMessageId, String actorEmail) {
        DlqMessage msg = dlqMessageRepository.findById(dlqMessageId)
                .orElseThrow(() -> new NotFoundException("Mensagem DLQ não encontrada."));

        dlqMessageRepository.updateStatus(dlqMessageId, "DISCARDED", Instant.now(), actorEmail);

        log.info("Admin: DLQ message descartada: id={} processo={}",
                dlqMessageId, msg.getProcessNumber());
    }

    @Audited(action = "REQUEUE_ALL_DLQ", entityType = "DLQ")
    @Transactional
    public int requeueAll(String actorEmail) {
        List<DlqMessage> pending = dlqMessageRepository.findByStatus("PENDING");
        for (DlqMessage msg : pending) {
            try {
                requeue(msg.getId(), actorEmail);
            } catch (Exception e) {
                log.error("Admin: falha ao requeuear DLQ id={}: {}", msg.getId(), e.getMessage());
            }
        }
        log.info("Admin: requeueAll concluído: {} mensagens requeueadas.", pending.size());
        return pending.size();
    }

    public long countPending() {
        return dlqMessageRepository.countByStatus("PENDING");
    }

    private DlqMessageResponse toResponse(DlqMessage msg) {
        return new DlqMessageResponse(
                msg.getId(), msg.getProcessNumber(), msg.getCourtCode(),
                msg.getRetryCount(), msg.getErrorMessage(), msg.getStatus(), msg.getQueuedAt()
        );
    }
}