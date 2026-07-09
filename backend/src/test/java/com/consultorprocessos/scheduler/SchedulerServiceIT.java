package com.consultorprocessos.scheduler;

import com.consultorprocessos.scheduler.model.CrawlRequestMessage;
import com.consultorprocessos.scheduler.service.SchedulerService;
import com.consultorprocessos.shared.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static com.consultorprocessos.shared.config.RabbitConfig.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

@Tag("integration")
@TestPropertySource(properties = "app.scheduler.enabled=true")
class SchedulerServiceIT extends BaseIntegrationTest {

    @Autowired
    private SchedulerService schedulerService;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private static final String TEST_EMAIL  = "scheduler-test@teste.com";
    private static final String TEST_PASS   = "Senha@123";

    @BeforeEach
    void setUp() throws Exception {
        clearCapturedEmails();
        registerVerifyAndGetToken(TEST_EMAIL, TEST_PASS);
        activateCourt("STF");
    }

    @Test
    @DisplayName("deve publicar mensagem para processo com last_checked_at nulo")
    void shouldPublishMessageForNeverCheckedProcess() {
        createProcessWithSubscription(TEST_EMAIL, cnj(100), "STF");

        schedulerService.scheduleAll();

        ArgumentCaptor<CrawlRequestMessage> captor =
                ArgumentCaptor.forClass(CrawlRequestMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE_CRAWL), eq(ROUTING_KEY_CRAWL_REQUEST), captor.capture());

        CrawlRequestMessage msg = captor.getValue();
        assertThat(msg.processNumber()).isEqualTo(cnj(100));
        assertThat(msg.courtCode()).isEqualTo("STF");
        assertThat(msg.retryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("não deve publicar mensagem para processo com tribunal inativo")
    void shouldNotPublishForInactiveCourt() {
        deactivateCourt("STF");
        createProcessWithSubscription(TEST_EMAIL, cnj(101), "STF");

        schedulerService.scheduleAll();

        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(CrawlRequestMessage.class));
    }

    @Test
    @DisplayName("não deve publicar para processo sem assinatura ativa")
    void shouldNotPublishForProcessWithoutActiveSubscription() {
        UUID courtId = jdbcTemplate.queryForObject(
                "SELECT id FROM courts WHERE code = 'STF'", UUID.class);
        jdbcTemplate.update("""
                INSERT INTO processes (id, process_number, process_number_raw, court_id, status)
                VALUES (gen_random_uuid(), ?, ?, ?, 'PENDING')
                """, cnj(102), cnj(102), courtId);

        schedulerService.scheduleAll();

        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(CrawlRequestMessage.class));
    }

    @Test
    @DisplayName("não deve publicar para processo com status BLOCKED")
    void shouldNotPublishForBlockedProcess() {
        createProcessWithSubscription(TEST_EMAIL, cnj(103), "STF");
        jdbcTemplate.update(
                "UPDATE processes SET status = 'BLOCKED' WHERE process_number = ?", cnj(103));

        schedulerService.scheduleAll();

        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(CrawlRequestMessage.class));
    }

    @Test
    @DisplayName("deve publicar múltiplas mensagens para múltiplos processos pendentes")
    void shouldPublishMultipleMessagesForMultipleProcesses() {
        createProcessWithSubscription(TEST_EMAIL, cnj(104), "STF");
        createProcessWithSubscription(TEST_EMAIL, cnj(105), "STF");
        createProcessWithSubscription(TEST_EMAIL, cnj(106), "STF");

        schedulerService.scheduleAll();

        verify(rabbitTemplate, times(3))
            .convertAndSend(
                eq(EXCHANGE_CRAWL),
                eq(ROUTING_KEY_CRAWL_REQUEST),
                any(CrawlRequestMessage.class));
    }

    @Test
    @DisplayName("não deve publicar quando não há processos pendentes")
    void shouldNotPublishWhenNoDueProcesses() {
        schedulerService.scheduleAll();

        verify(rabbitTemplate, never())
        .convertAndSend(
                anyString(),
                anyString(),
                any(CrawlRequestMessage.class));
    }
}