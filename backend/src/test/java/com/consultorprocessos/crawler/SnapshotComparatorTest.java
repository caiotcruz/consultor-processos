package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.entity.ParserVersion;
import com.consultorprocessos.crawler.event.MovimentacaoDetectadaEvent;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.Movement;
import com.consultorprocessos.crawler.repository.ParserVersionRepository;
import com.consultorprocessos.crawler.repository.ProcessSnapshotRepository;
import com.consultorprocessos.crawler.service.SnapshotComparator;
import com.consultorprocessos.process.entity.Process;
import com.consultorprocessos.process.repository.ProcessHistoryRepository;
import com.consultorprocessos.process.repository.ProcessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SnapshotComparatorTest {

    @Mock private ProcessRepository         processRepository;
    @Mock private ProcessSnapshotRepository snapshotRepository;
    @Mock private ProcessHistoryRepository  historyRepository;
    @Mock private ParserVersionRepository   parserVersionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SnapshotComparator comparator;

    private Process       process;
    private ParserVersion parserVersion;
    private UUID          processId;

    @BeforeEach
    void setUp() {
        processId = UUID.randomUUID();

        process = new Process();
        setId(process, processId);
        process.setProcessNumber("0001234-55.2020.8.26.0001");

        parserVersion = new ParserVersion();
        parserVersion.setVersion("1.0.0");
        parserVersion.setActive(true);
    }

    @Test
    @DisplayName("não deve publicar evento quando hash for idêntico")
    void shouldNotPublishEventWhenHashUnchanged() {
        process.setLastSnapshotHash("hash-antigo");

        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        CrawlerSnapshot snapshot = buildSnapshot("hash-antigo");

        comparator.compareAndPersist(processId, snapshot);

        verify(eventPublisher, never()).publishEvent(any());
        verify(snapshotRepository, never()).save(any());
        verify(processRepository).updateLastChecked(eq(processId), any());
    }

    @Test
    @DisplayName("deve salvar snapshot, histórico e publicar evento quando hash mudar")
    void shouldSaveAndPublishEventWhenHashChanged() {
        process.setLastSnapshotHash("hash-antigo");

        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(parserVersionRepository.findByCourtCodeAndActiveTrue("STF"))
                .thenReturn(Optional.of(parserVersion));
        when(snapshotRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        CrawlerSnapshot snapshot = buildSnapshot("hash-novo");
        comparator.compareAndPersist(processId, snapshot);

        verify(snapshotRepository).save(any());
        verify(historyRepository).saveAll(argThat(list ->
                ((List<?>) list).size() == snapshot.movements().size()));

        ArgumentCaptor<MovimentacaoDetectadaEvent> eventCaptor =
                ArgumentCaptor.forClass(MovimentacaoDetectadaEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getProcessId()).isEqualTo(processId);
    }

    @Test
    @DisplayName("primeiro snapshot (hash nulo) deve ser tratado como mudança")
    void shouldTreatFirstSnapshotAsChange() {
        process.setLastSnapshotHash(null);

        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(parserVersionRepository.findByCourtCodeAndActiveTrue("STF"))
                .thenReturn(Optional.of(parserVersion));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CrawlerSnapshot snapshot = buildSnapshot("hash-qualquer");
        comparator.compareAndPersist(processId, snapshot);

        verify(snapshotRepository).save(any());
        verify(eventPublisher).publishEvent(any(MovimentacaoDetectadaEvent.class));
    }

    @Test
    @DisplayName("deve resetar consecutiveErrors ao detectar snapshot sem mudança")
    void shouldResetErrorsOnSuccessfulComparison() {
        process.setLastSnapshotHash("mesmo-hash");
        process.setConsecutiveErrors(2);

        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        CrawlerSnapshot snapshot = buildSnapshot("mesmo-hash");

        comparator.compareAndPersist(processId, snapshot);

        verify(processRepository).resetConsecutiveErrors(processId);
    }

    private CrawlerSnapshot buildSnapshot(String hash) {
        return new CrawlerSnapshot(
                "0001234-55.2020.8.26.0001",
                "STF",
                hash,
                "{\"processNumber\":\"0001234-55.2020.8.26.0001\",\"movements\":[]}",
                List.of(new Movement(LocalDate.of(2025, 3, 15), "Conclusos.")),
                CrawlerStrategy.HTTP,
                "1.0.0",
                Instant.now()
        );
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}