package com.consultorprocessos.crawler.service;

import com.consultorprocessos.crawler.entity.ParserVersion;
import com.consultorprocessos.crawler.entity.ProcessSnapshot;
import com.consultorprocessos.crawler.event.MovimentacaoDetectadaEvent;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.Movement;
import com.consultorprocessos.crawler.repository.ParserVersionRepository;
import com.consultorprocessos.crawler.repository.ProcessSnapshotRepository;
import com.consultorprocessos.process.entity.Process;
import com.consultorprocessos.process.entity.ProcessHistory;
import com.consultorprocessos.process.repository.ProcessHistoryRepository;
import com.consultorprocessos.process.repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnapshotComparator {

    private final ProcessRepository         processRepository;
    private final ProcessSnapshotRepository snapshotRepository;
    private final ProcessHistoryRepository  historyRepository;
    private final ParserVersionRepository   parserVersionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void compareAndPersist(UUID processId, CrawlerSnapshot incoming) {
        Process process = processRepository.findById(processId)
                .orElseThrow(() -> new IllegalStateException(
                        "Processo não encontrado para comparação: " + processId));

        String previousHash = process.getLastSnapshotHash();
        String newHash      = incoming.contentHash();

        if (newHash.equals(previousHash)) {
            processRepository.updateLastChecked(processId, Instant.now());
            processRepository.resetConsecutiveErrors(processId);
            log.debug("Hash idêntico — sem mudança. processo={}",
                    process.getProcessNumber());
            return;
        }

        log.info("Mudança detectada! processo={} hashAnterior={} hashNovo={}",
                process.getProcessNumber(),
                previousHash != null ? previousHash.substring(0, 8) + "..." : "null",
                newHash.substring(0, 8) + "...");

        ParserVersion parserVersion = parserVersionRepository
                .findByCourtCodeAndActiveTrue(incoming.courtCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhuma versão de parser ativa para: " + incoming.courtCode()));

        ProcessSnapshot savedSnapshot = snapshotRepository.save(
                buildSnapshotEntity(process, incoming, parserVersion));

        List<ProcessHistory> historyEntries = incoming.movements().stream()
                .map(m -> buildHistoryEntry(process, savedSnapshot, m))
                .toList();
        historyRepository.saveAll(historyEntries);

        process.setLastSnapshotHash(newHash);
        process.setLastMovementAt(Instant.now());
        process.setLastCheckedAt(Instant.now());
        process.setConsecutiveErrors(0);
        processRepository.save(process);

        eventPublisher.publishEvent(
                new MovimentacaoDetectadaEvent(this, processId, incoming));

        log.info("Snapshot persistido e evento publicado. processo={} movimentações={}",
                process.getProcessNumber(), historyEntries.size());
    }

    private ProcessSnapshot buildSnapshotEntity(Process        process,
                                                CrawlerSnapshot incoming,
                                                ParserVersion   parserVersion) {
        ProcessSnapshot entity = new ProcessSnapshot();
        entity.setProcess(process);
        entity.setContentHash(incoming.contentHash());
        entity.setRawContent(incoming.rawContentJson());
        entity.setParserVersion(parserVersion);
        entity.setCrawlerStrategy(incoming.strategyUsed());
        return entity;
    }

    private ProcessHistory buildHistoryEntry(Process         process,
                                             ProcessSnapshot snapshot,
                                             Movement        movement) {
        ProcessHistory history = new ProcessHistory();
        history.setProcess(process);
        history.setSnapshot(snapshot);
        history.setDescription(movement.description());
        history.setMovementDate(movement.date());
        return history;
    }
}