package com.consultorprocessos.admin.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class SmtpAdminEmailService implements AdminEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.sender-address}")
    private String senderAddress;

    @Value("${app.notifications.admin-email}")
    private String adminEmail;

    @Override
    public void sendCourtRequestAlert(String courtName, String courtCode,
                                      String processNumber, long totalRequests) {
        String subject = "[Admin] Nova solicitação de tribunal: " + courtName;
        String body    = String.format("""
                <p>Uma nova solicitação de tribunal foi registrada.</p>
                <ul>
                  <li><b>Tribunal:</b> %s</li>
                  <li><b>Código:</b> %s</li>
                  <li><b>Processo:</b> %s</li>
                  <li><b>Total de solicitações:</b> %d</li>
                </ul>
                <p>Acesse o painel admin para atualizar o status.</p>
                """, courtName, courtCode, processNumber, totalRequests);
        send(adminEmail, subject, body);
    }

    @Override
    public void sendHealthScoreAlert(String courtCode, int score) {
        String subject = "[Admin] Health score baixo: " + courtCode;
        String body    = String.format("""
                <p>O health score do tribunal <b>%s</b> caiu para <b>%d</b>.</p>
                <p>Verifique o painel admin para detalhes.</p>
                """, courtCode, score);
        send(adminEmail, subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage mime   = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mime, true, "UTF-8");
            h.setFrom(senderAddress);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(htmlBody, true);
            mailSender.send(mime);
        } catch (MessagingException | org.springframework.mail.MailException e) {
            log.error("Falha ao enviar e-mail admin para {}: {}", to, e.getMessage());
        }
    }
}