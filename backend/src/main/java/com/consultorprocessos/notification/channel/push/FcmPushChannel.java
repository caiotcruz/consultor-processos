package com.consultorprocessos.notification.channel.push;

import com.consultorprocessos.notification.channel.NotificationChannel;
import com.consultorprocessos.notification.channel.NotificationPayload;
import com.consultorprocessos.notification.channel.PushNotificationChannel;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class FcmPushChannel implements PushNotificationChannel  {

    @Override
    public void send(NotificationPayload payload) {
        List<String> tokens = payload.deviceTokens();
        if (tokens == null || tokens.isEmpty()) return;

        String title = "Nova movimentação no processo";
        String body  = payload.processNumber() + " — " +
                       payload.movements().get(0).description();

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("processNumber", payload.processNumber())
                .putData("processId",     payload.processId().toString())
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

            log.info("[FCM] Enviado: tokens={} sucessos={} falhas={}",
                    tokens.size(),
                    response.getSuccessCount(),
                    response.getFailureCount());

            if (response.getFailureCount() > 0) {
                response.getResponses().stream()
                        .filter(r -> !r.isSuccessful())
                        .forEach(r -> log.warn("[FCM] Falha em token: {}",
                                r.getException().getMessage()));
            }
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException("Falha ao enviar push via FCM: " + e.getMessage(), e);
        }
    }

    @Override
    public String getChannelCode() { return "PUSH"; }
}