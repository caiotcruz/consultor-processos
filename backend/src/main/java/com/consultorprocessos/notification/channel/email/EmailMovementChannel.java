package com.consultorprocessos.notification.channel.email;

import com.consultorprocessos.notification.channel.EmailNotificationChannel;
import com.consultorprocessos.notification.channel.NotificationChannel;
import com.consultorprocessos.notification.channel.NotificationPayload;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class EmailMovementChannel implements EmailNotificationChannel  {

    private final JavaMailSender  mailSender;
    private final TemplateEngine  templateEngine;

    @Value("${app.email.sender-address}")
    private String senderAddress;

    @Value("${app.email.sender-name}")
    private String senderName;

    @Override
    public void send(NotificationPayload payload) {
        try {
            String htmlBody = renderTemplate(payload);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(senderAddress, senderName);
            helper.setTo(payload.userEmail());
            helper.setSubject(buildSubject(payload));
            helper.setText(htmlBody, true);

            mailSender.send(mime);

            log.info("[EMAIL] Notificação enviada: para={} processo={}",
                    payload.userEmail(), payload.processNumber());

        } catch (MessagingException | UnsupportedEncodingException | org.springframework.mail.MailException e) {
            throw new RuntimeException(
                    "Falha ao enviar e-mail para " + payload.userEmail() + ": " + e.getMessage(), e);
        }
    }

    private String renderTemplate(NotificationPayload payload) {
        Context ctx = new Context(Locale.forLanguageTag("pt-BR"));
        ctx.setVariable("userName",        payload.userName());
        ctx.setVariable("processNumber",   payload.processNumber());
        ctx.setVariable("courtName",       payload.courtName());
        ctx.setVariable("movimentos",      payload.movements());
        ctx.setVariable("unsubscribeUrl",  payload.unsubscribeUrl());
        return templateEngine.process("notification-movimento", ctx);
    }

    private String buildSubject(NotificationPayload payload) {
        return "Nova movimentação — Processo " + payload.processNumber();
    }

    @Override
    public String getChannelCode() { return "EMAIL"; }
}