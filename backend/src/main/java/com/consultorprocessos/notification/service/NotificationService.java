package com.consultorprocessos.notification.service;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.crawler.event.MovimentacaoDetectadaEvent;
import com.consultorprocessos.court.repository.CourtRepository;
import com.consultorprocessos.notification.channel.EmailNotificationChannel;
import com.consultorprocessos.notification.channel.NotificationChannel;
import com.consultorprocessos.notification.channel.NotificationPayload;
import com.consultorprocessos.notification.channel.PushNotificationChannel;
import com.consultorprocessos.notification.channel.email.EmailMovementChannel;
import com.consultorprocessos.notification.channel.email.LogEmailMovementChannel;
import com.consultorprocessos.notification.channel.push.FcmPushChannel;
import com.consultorprocessos.notification.channel.push.LogPushChannel;
import com.consultorprocessos.notification.repository.UserDeviceTokenRepository;
import com.consultorprocessos.process.entity.Process;
import com.consultorprocessos.process.entity.ProcessSubscription;
import com.consultorprocessos.process.repository.ProcessRepository;
import com.consultorprocessos.process.repository.ProcessSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ProcessSubscriptionRepository subscriptionRepository;
    private final ProcessRepository             processRepository;
    private final UserDeviceTokenRepository     deviceTokenRepository;
    private final EmailNotificationChannel            emailChannel;
    private final PushNotificationChannel             pushChannel;
    private final NotificationHistoryService    historyService;
    private final NotificationRateLimiter       rateLimiter;
    private final UnsubscribeTokenService       unsubscribeTokenService;

    @Value("${app.notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.notifications.push.enabled:false}")
    private boolean pushEnabled;

    @Value("${app.notifications.unsubscribe-base-url}")
    private String unsubscribeBaseUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMovimentacaoDetectada(MovimentacaoDetectadaEvent event) {
        log.debug("NotificationService: processando evento para processo={}",
                event.getProcessId());

        Process process = processRepository.findById(event.getProcessId()).orElse(null);
        if (process == null) {
            log.warn("NotificationService: processo {} não encontrado.", event.getProcessId());
            return;
        }

        List<ProcessSubscription> activeSubscriptions =
                subscriptionRepository.findByProcessIdAndActiveTrue(event.getProcessId());

        if (activeSubscriptions.isEmpty()) {
            log.debug("NotificationService: nenhum assinante ativo para o processo {}.",
                    process.getProcessNumber());
            return;
        }

        for (ProcessSubscription sub : activeSubscriptions) {
            try {
                notifyUser(sub.getUser(), process, event);
            } catch (Exception e) {
                log.error("NotificationService: falha ao notificar userId={}. Erro: {}",
                        sub.getUser().getId(), e.getMessage());
            }
        }
    }

    public void notifyUser(User user, Process process, MovimentacaoDetectadaEvent event) {
        List<String> deviceTokens = deviceTokenRepository.findByUserId(user.getId())
                .stream()
                .map(t -> t.getToken())
                .toList();

        String unsubscribeUrl = unsubscribeTokenService.buildUrl(unsubscribeBaseUrl, user.getId());

        NotificationPayload payload = new NotificationPayload(
                user.getId(),
                process.getId(),
                process.getProcessNumber(),
                process.getCourt().getName(),
                user.getEmail(),
                user.getName(),
                deviceTokens,
                event.getSnapshot().movements(),
                unsubscribeUrl
        );

        if (emailEnabled && user.getNotificationPreferences().isEmailEnabled()) {
            sendViaChannel(emailChannel, payload, user, process.getId());
        }

        if (pushEnabled && user.getNotificationPreferences().isPushEnabled()
                && !deviceTokens.isEmpty()) {
            sendViaChannel(pushChannel, payload, user, process.getId());
        }
    }

    private void sendViaChannel(NotificationChannel channel, NotificationPayload payload,
                                 User user, UUID processId) {
        String channelCode = channel.getChannelCode();

        if (!rateLimiter.isAllowed(user.getId(), channelCode)) {
            log.info("NotificationService: rate limit atingido. userId={} canal={}",
                    user.getId(), channelCode);
            historyService.record(user, processId, channelCode,
                    "MOVIMENTO_DETECTADO", "SKIPPED", "Rate limit excedido");
            return;
        }

        try {
            channel.send(payload);
            historyService.record(user, processId, channelCode,
                    "MOVIMENTO_DETECTADO", "SENT", null);
            log.info("NotificationService: enviado. userId={} canal={} processo={}",
                    user.getId(), channelCode, payload.processNumber());
        } catch (Exception e) {
            historyService.record(user, processId, channelCode,
                    "MOVIMENTO_DETECTADO", "FAILED", e.getMessage());
            log.error("NotificationService: falha ao enviar. userId={} canal={} erro={}",
                    user.getId(), channelCode, e.getMessage());
        }
    }
}