// src/test/java/com/consultorprocessos/notification/NotificationServiceTest.java
package com.consultorprocessos.notification;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.entity.UserStatus;
import com.consultorprocessos.court.entity.Court;
import com.consultorprocessos.crawler.event.MovimentacaoDetectadaEvent;
import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.model.CrawlerStrategy;
import com.consultorprocessos.crawler.model.Movement;
import com.consultorprocessos.notification.channel.EmailNotificationChannel;
import com.consultorprocessos.notification.channel.NotificationPayload;
import com.consultorprocessos.notification.channel.PushNotificationChannel;
import com.consultorprocessos.notification.entity.UserDeviceToken;
import com.consultorprocessos.notification.repository.UserDeviceTokenRepository;
import com.consultorprocessos.notification.service.NotificationHistoryService;
import com.consultorprocessos.notification.service.NotificationRateLimiter;
import com.consultorprocessos.notification.service.NotificationService;
import com.consultorprocessos.notification.service.UnsubscribeTokenService;
import com.consultorprocessos.plan.entity.Plan;
import com.consultorprocessos.process.entity.Process;
import com.consultorprocessos.process.entity.ProcessSubscription;
import com.consultorprocessos.process.repository.ProcessRepository;
import com.consultorprocessos.process.repository.ProcessSubscriptionRepository;
import com.consultorprocessos.user.entity.UserNotificationPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private ProcessSubscriptionRepository subscriptionRepository;
    @Mock private ProcessRepository             processRepository;
    @Mock private UserDeviceTokenRepository     deviceTokenRepository;
    @Mock private EmailNotificationChannel      emailChannel;
    @Mock private PushNotificationChannel       pushChannel;
    @Mock private NotificationHistoryService    historyService;
    @Mock private NotificationRateLimiter       rateLimiter;
    @Mock private UnsubscribeTokenService       unsubscribeTokenService;

    private NotificationService notificationService;

    private User    user;
    private Process process;
    private UUID    processId;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                subscriptionRepository,
                processRepository,
                deviceTokenRepository,
                emailChannel,
                pushChannel,
                historyService,
                rateLimiter,
                unsubscribeTokenService
        );

        ReflectionTestUtils.setField(notificationService, "emailEnabled",       true);
        ReflectionTestUtils.setField(notificationService, "pushEnabled",        true);
        ReflectionTestUtils.setField(notificationService, "unsubscribeBaseUrl",
                "http://localhost:4200/unsubscribe");

        processId = UUID.randomUUID();

        user = new User();
        setField(user, "id", UUID.randomUUID());
        user.setName("Usuário Teste");
        user.setEmail("teste@teste.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of("ROLE_USER"));
        user.setNotificationPreferences(new UserNotificationPreferences(true, false));

        Plan plan = new Plan();
        setField(plan, "id", UUID.randomUUID());
        user.setPlan(plan);
        Court dummyCourt = new Court();
        dummyCourt.setName("Supremo Tribunal Federal");
        dummyCourt.setCode("STF");

        process = new Process();
        setField(process, "id", processId);
        process.setProcessNumber("0001234-55.2020.8.26.0001");
        process.setCourt(dummyCourt);

        lenient().when(emailChannel.getChannelCode()).thenReturn("EMAIL");
        lenient().when(pushChannel.getChannelCode()).thenReturn("PUSH");
        lenient().when(unsubscribeTokenService.buildUrl(anyString(), any()))
                 .thenReturn("http://unsub");
        lenient().when(rateLimiter.isAllowed(any(), anyString())).thenReturn(true);
        lenient().when(deviceTokenRepository.findByUserId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("deve enviar e-mail quando usuario tem email habilitado")
    void shouldSendEmailWhenEmailEnabled() {
        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(subscriptionRepository.findByProcessIdAndActiveTrue(processId))
                .thenReturn(List.of(buildSubscription()));

        notificationService.onMovimentacaoDetectada(buildEvent());

        verify(emailChannel).send(any(NotificationPayload.class));
        verify(historyService).record(eq(user), eq(processId), eq("EMAIL"),
                eq("MOVIMENTO_DETECTADO"), eq("SENT"), isNull());
    }

    @Test
    @DisplayName("não deve enviar e-mail quando usuario desabilitou notificações por e-mail")
    void shouldNotSendEmailWhenEmailDisabledByUser() {
        user.getNotificationPreferences().setEmailEnabled(false);
        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(subscriptionRepository.findByProcessIdAndActiveTrue(processId))
                .thenReturn(List.of(buildSubscription()));

        notificationService.onMovimentacaoDetectada(buildEvent());

        verify(emailChannel, never()).send(any());
    }

    @Test
    @DisplayName("deve gravar SKIPPED quando rate limit é excedido")
    void shouldRecordSkippedWhenRateLimitExceeded() {
        when(rateLimiter.isAllowed(user.getId(), "EMAIL")).thenReturn(false);
        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(subscriptionRepository.findByProcessIdAndActiveTrue(processId))
                .thenReturn(List.of(buildSubscription()));

        notificationService.onMovimentacaoDetectada(buildEvent());

        verify(emailChannel, never()).send(any());
        verify(historyService).record(eq(user), eq(processId), eq("EMAIL"),
                eq("MOVIMENTO_DETECTADO"), eq("SKIPPED"), contains("Rate limit"));
    }

    @Test
    @DisplayName("deve gravar FAILED quando canal lança exceção")
    void shouldRecordFailedWhenChannelThrows() {
        doThrow(new RuntimeException("SMTP timeout")).when(emailChannel).send(any());
        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(subscriptionRepository.findByProcessIdAndActiveTrue(processId))
                .thenReturn(List.of(buildSubscription()));

        notificationService.onMovimentacaoDetectada(buildEvent());

        verify(historyService).record(eq(user), eq(processId), eq("EMAIL"),
                eq("MOVIMENTO_DETECTADO"), eq("FAILED"), contains("SMTP timeout"));
    }

    @Test
    @DisplayName("deve enviar push quando usuario tem push habilitado e dispositivos registrados")
    void shouldSendPushWhenPushEnabledAndDevicesExist() {
        user.getNotificationPreferences().setPushEnabled(true);

        UserDeviceToken token = new UserDeviceToken();
        setField(token, "id", UUID.randomUUID());
        token.setToken("fcm-token-123");
        token.setPlatform("ANDROID");

        when(deviceTokenRepository.findByUserId(user.getId())).thenReturn(List.of(token));
        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(subscriptionRepository.findByProcessIdAndActiveTrue(processId))
                .thenReturn(List.of(buildSubscription()));

        notificationService.onMovimentacaoDetectada(buildEvent());

        ArgumentCaptor<NotificationPayload> captor =
                ArgumentCaptor.forClass(NotificationPayload.class);
        verify(pushChannel).send(captor.capture());
        assertThat(captor.getValue().deviceTokens()).contains("fcm-token-123");
    }

    @Test
    @DisplayName("não deve enviar push quando usuario não tem dispositivos registrados")
    void shouldNotSendPushWhenNoDevicesRegistered() {
        user.getNotificationPreferences().setPushEnabled(true);
        when(deviceTokenRepository.findByUserId(any())).thenReturn(List.of());
        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(subscriptionRepository.findByProcessIdAndActiveTrue(processId))
                .thenReturn(List.of(buildSubscription()));

        notificationService.onMovimentacaoDetectada(buildEvent());

        verify(pushChannel, never()).send(any());
    }

    @Test
    @DisplayName("deve ignorar silenciosamente quando processo não existe")
    void shouldIgnoreWhenProcessNotFound() {
        when(processRepository.findById(processId)).thenReturn(Optional.empty());

        notificationService.onMovimentacaoDetectada(buildEvent());

        verify(subscriptionRepository, never()).findByProcessIdAndActiveTrue(any());
    }

    @Test
    @DisplayName("deve ignorar quando não há assinantes ativos")
    void shouldIgnoreWhenNoActiveSubscribers() {
        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(subscriptionRepository.findByProcessIdAndActiveTrue(processId))
                .thenReturn(List.of());

        notificationService.onMovimentacaoDetectada(buildEvent());

        verify(emailChannel, never()).send(any());
        verify(pushChannel, never()).send(any());
    }

    @Test
    @DisplayName("deve incluir URL de descadastro no payload de e-mail")
    void shouldIncludeUnsubscribeUrlInEmailPayload() {
        when(processRepository.findById(processId)).thenReturn(Optional.of(process));
        when(subscriptionRepository.findByProcessIdAndActiveTrue(processId))
                .thenReturn(List.of(buildSubscription()));

        notificationService.onMovimentacaoDetectada(buildEvent());

        ArgumentCaptor<NotificationPayload> captor =
                ArgumentCaptor.forClass(NotificationPayload.class);
        verify(emailChannel).send(captor.capture());
        assertThat(captor.getValue().unsubscribeUrl()).isEqualTo("http://unsub");
    }

    private MovimentacaoDetectadaEvent buildEvent() {
        List<Movement> movements = List.of(
                new Movement(LocalDate.of(2025, 3, 15), "Conclusos ao relator."));
        CrawlerSnapshot snapshot = new CrawlerSnapshot(
                "0001234-55.2020.8.26.0001", "STF",
                "hash123", "{}", movements,
                CrawlerStrategy.HTTP, "1.0.0", Instant.now());
        return new MovimentacaoDetectadaEvent(this, processId, snapshot);
    }

    private ProcessSubscription buildSubscription() {
        ProcessSubscription sub = new ProcessSubscription();
        sub.setUser(user);
        sub.setProcess(process);
        sub.setActive(true);
        return sub;
    }

    private void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("setField falhou para " + name, e);
        }
    }
}