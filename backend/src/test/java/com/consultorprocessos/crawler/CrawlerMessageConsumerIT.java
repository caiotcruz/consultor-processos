package com.consultorprocessos.crawler;

import com.consultorprocessos.crawler.exception.CourtBlockedException;
import com.consultorprocessos.crawler.exception.CourtUnavailableException;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.provider.CourtProviderFactory;
import com.consultorprocessos.crawler.repository.CrawlerExecutionRepository;
import com.consultorprocessos.crawler.repository.ProcessSnapshotRepository;
import com.consultorprocessos.process.entity.ProcessStatus;
import com.consultorprocessos.process.repository.ProcessRepository;
import com.consultorprocessos.scheduler.model.CrawlRequestMessage;
import com.consultorprocessos.shared.BaseIntegrationTest;
import com.consultorprocessos.shared.config.RabbitConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("integration")
class CrawlerMessageConsumerIT extends BaseIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private ProcessSnapshotRepository snapshotRepository;

    @Autowired
    private CrawlerExecutionRepository executionRepository;

    @MockBean
    private CourtProviderFactory providerFactory;

    private CourtProvider mockProvider;

    private static final String TEST_EMAIL = "consumer-test@teste.com";
    private static final String TEST_PASS  = "Senha@123";

    @BeforeEach
        void setUp() throws Exception {
        clearCapturedEmails();
        registerVerifyAndGetToken(TEST_EMAIL, TEST_PASS);
        
        activateCourt("STF");

        UUID courtId = jdbcTemplate.queryForObject(
                "SELECT id FROM courts WHERE code = 'STF'", UUID.class);

        jdbcTemplate.update("DELETE FROM parser_versions WHERE court_id = ? AND version = '1.0.0'", courtId);

        jdbcTemplate.update("""
                INSERT INTO parser_versions (id, court_id, version, active, released_at, released_by)
                VALUES (gen_random_uuid(), ?, '1.0.0', true, NOW(), 'test-setup')
                """, courtId);

        mockProvider = mock(CourtProvider.class);
        when(providerFactory.getProvider(anyString())).thenReturn(mockProvider);
        }

    @Test
    @DisplayName("deve processar mensagem, criar snapshot e marcar processo como OK")
    void shouldProcessMessageAndCreateSnapshot() throws Exception {
        UUID processId = createProcessWithSubscription(TEST_EMAIL, cnj(200), "STF");
        CrawlerSnapshot snapshot = buildMockSnapshot(cnj(200), "STF");
        when(mockProvider.consultar(cnj(200))).thenReturn(snapshot);

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL,
                RabbitConfig.ROUTING_KEY_CRAWL_REQUEST,
                buildCrawlMessage(processId, cnj(200), "STF")
        );

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> processRepository.findById(processId)
                        .map(p -> ProcessStatus.OK.equals(p.getStatus()))
                        .orElse(false));

        assertThat(snapshotRepository.findFirstByProcessIdOrderByCapturedAtDesc(processId))
                .isPresent();

        assertThat(executionRepository.count()).isGreaterThan(0);

        assertThat(processRepository.findById(processId))
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(ProcessStatus.OK));
    }

    @Test
    @DisplayName("deve marcar processo como BLOCKED quando tribunal bloqueia")
    void shouldMarkProcessAsBlockedWhenCourtBlocks() throws Exception {
        UUID processId = createProcessWithSubscription(TEST_EMAIL, cnj(201), "STF");
        when(mockProvider.consultar(cnj(201)))
                .thenThrow(new CourtBlockedException("Captcha detectado."));

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL,
                RabbitConfig.ROUTING_KEY_CRAWL_REQUEST,
                buildCrawlMessage(processId, cnj(201), "STF")
        );

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .until(() -> processRepository.findById(processId)
                        .map(p -> ProcessStatus.BLOCKED.equals(p.getStatus()))
                        .orElse(false));

        assertThat(processRepository.findById(processId))
                .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(ProcessStatus.BLOCKED));

        assertThat(executionRepository.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("deve publicar na fila de retry quando tribunal está indisponível")
    void shouldPublishToRetryQueueWhenCourtUnavailable() throws Exception {
        UUID processId = createProcessWithSubscription(TEST_EMAIL, cnj(202), "STF");
        when(mockProvider.consultar(cnj(202)))
                .thenThrow(new CourtUnavailableException("STF", "Timeout na conexão."));

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL,
                RabbitConfig.ROUTING_KEY_CRAWL_REQUEST,
                buildCrawlMessage(processId, cnj(202), "STF")
        );

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .until(() -> {
                    return executionRepository.findAll().stream()
                            .anyMatch(e -> !e.isSuccess());
                });

        assertThat(processRepository.findById(processId))
                .hasValueSatisfying(p -> assertThat(p.getStatus()).isNotEqualTo(ProcessStatus.ERROR));
    }

    @Test
    @DisplayName("deve manter idempotência: segundo crawl idêntico não cria novo snapshot")
    void shouldNotCreateNewSnapshotWhenHashUnchanged() throws Exception {
        UUID processId = createProcessWithSubscription(TEST_EMAIL, cnj(203), "STF");
        CrawlerSnapshot snapshot = buildMockSnapshot(cnj(203), "STF");
        when(mockProvider.consultar(cnj(203))).thenReturn(snapshot);

        CrawlRequestMessage msg = buildCrawlMessage(processId, cnj(203), "STF");

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL, RabbitConfig.ROUTING_KEY_CRAWL_REQUEST, msg);

        Awaitility.await().atMost(15, TimeUnit.SECONDS)
                .until(() -> ProcessStatus.OK.equals(
                        processRepository.findById(processId)
                                .map(com.consultorprocessos.process.entity.Process::getStatus)
                                .orElse(ProcessStatus.PENDING)));

        long snapshotsAfterFirst = snapshotRepository.count();

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL, RabbitConfig.ROUTING_KEY_CRAWL_REQUEST, msg);

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> executionRepository.count() >= 2);

        assertThat(snapshotRepository.count()).isEqualTo(snapshotsAfterFirst);
    }

    @Test
    @DisplayName("deve ignorar silenciosamente mensagem para processo inexistente")
    void shouldIgnoreMessageForNonExistentProcess() throws Exception {
        UUID phantomId = UUID.randomUUID();
        UUID courtId   = jdbcTemplate.queryForObject(
                "SELECT id FROM courts WHERE code = 'STF'", UUID.class);

        CrawlRequestMessage msg = new CrawlRequestMessage(
                phantomId, courtId, cnj(204), "STF", 0);

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_CRAWL, RabbitConfig.ROUTING_KEY_CRAWL_REQUEST, msg);

        Thread.sleep(3000);

        verify(mockProvider, never()).consultar(anyString());
    }
}