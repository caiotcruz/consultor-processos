package com.consultorprocessos.notification.channel.push;

import com.consultorprocessos.notification.channel.NotificationChannel;
import com.consultorprocessos.notification.channel.NotificationPayload;
import com.consultorprocessos.notification.channel.PushNotificationChannel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
@Slf4j
public class LogPushChannel implements PushNotificationChannel  {

    @Override
    public void send(NotificationPayload payload) {
        log.info("[PUSH MOCK] Notificação: tokens={} processo={} movimentos={}",
                payload.deviceTokens().size(),
                payload.processNumber(),
                payload.movements().size());
        payload.deviceTokens().forEach(t ->
                log.info("  → token={}", t.substring(0, Math.min(t.length(), 20)) + "..."));
    }

    @Override
    public String getChannelCode() { return "PUSH"; }
}