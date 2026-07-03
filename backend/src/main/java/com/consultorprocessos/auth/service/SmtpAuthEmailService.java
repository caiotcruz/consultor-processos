package com.consultorprocessos.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class SmtpAuthEmailService implements AuthEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.sender-address}")
    private String senderAddress;

    @Value("${app.email.sender-name}")
    private String senderName;

    @Value("${app.notifications.unsubscribe-base-url}")
    private String appBaseUrl;

    @Override
    public void sendVerificationEmail(String to, String rawToken) {
        String link    = appBaseUrl.replace("/unsubscribe", "") +
                         "/verify-email?token=" + rawToken;
        String subject = "Verifique seu e-mail — Consultor de Processos";
        String body    = buildVerificationBody(link);
        send(to, subject, body);
    }

    @Override
    public void sendPasswordResetEmail(String to, String rawToken) {
        String link    = appBaseUrl.replace("/unsubscribe", "") +
                         "/reset-password?token=" + rawToken;
        String subject = "Redefinição de senha — Consultor de Processos";
        String body    = buildPasswordResetBody(link);
        send(to, subject, body);
    }

    @Override
    public void sendPasswordResetConfirmationEmail(String to) {
        send(to,
            "Sua senha foi redefinida — Consultor de Processos",
            "<p>Sua senha foi redefinida com sucesso.</p>" +
            "<p>Se você não fez essa alteração, entre em contato imediatamente.</p>");
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage mime   = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(senderAddress, senderName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(mime);
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Falha ao enviar e-mail de auth para {}: {}", to, e.getMessage());
        }
    }

    private String buildVerificationBody(String link) {
        return "<p>Olá!</p>" +
               "<p>Clique no link abaixo para verificar seu e-mail:</p>" +
               "<p><a href=\"" + link + "\">Verificar e-mail</a></p>" +
               "<p>O link expira em 24 horas.</p>" +
               "<p>Se você não criou uma conta, ignore este e-mail.</p>";
    }

    private String buildPasswordResetBody(String link) {
        return "<p>Olá!</p>" +
               "<p>Recebemos uma solicitação para redefinir sua senha.</p>" +
               "<p><a href=\"" + link + "\">Redefinir senha</a></p>" +
               "<p>O link expira em 1 hora e só pode ser usado uma vez.</p>" +
               "<p>Se você não fez essa solicitação, ignore este e-mail.</p>";
    }
}