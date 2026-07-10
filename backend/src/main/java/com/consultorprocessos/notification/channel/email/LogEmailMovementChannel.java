package com.consultorprocessos.notification.channel.email;

import com.consultorprocessos.notification.channel.EmailNotificationChannel;
import com.consultorprocessos.notification.channel.NotificationChannel;
import com.consultorprocessos.notification.channel.NotificationPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Profile({"dev", "test"})
@Slf4j
public class LogEmailMovementChannel implements EmailNotificationChannel  {

    public record SentMovementEmail(String to, String processNumber, int movementCount) {}

    private final List<SentMovementEmail> sentEmails =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(NotificationPayload payload) {
        log.info("[EMAIL MOCK] Movimentação: para={} processo={} movimentos={}",
                payload.userEmail(), payload.processNumber(), payload.movements().size());
        payload.movements().forEach(m ->
                log.info("  → {} | {}", m.date(), m.description()));
        log.info("  Descadastro: {}", payload.unsubscribeUrl());

        sentEmails.add(new SentMovementEmail(
                payload.userEmail(),
                payload.processNumber(),
                payload.movements().size()
        ));
    }

    public List<SentMovementEmail> getSentEmails() {
        return Collections.unmodifiableList(sentEmails);
    }

    public void clear() {
        sentEmails.clear();
    }

    @Override
    public String getChannelCode() { return "EMAIL"; }
}