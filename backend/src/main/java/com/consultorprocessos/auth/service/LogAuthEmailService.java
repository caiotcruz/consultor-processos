package com.consultorprocessos.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Primary
@Profile({"dev", "test"})
@Slf4j
public class LogAuthEmailService implements AuthEmailService {

    public record SentEmail(String to, EmailType type, String token) {}

    public enum EmailType { VERIFICATION, PASSWORD_RESET, PASSWORD_RESET_CONFIRMATION }

    private final List<SentEmail> sentEmails =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public void sendVerificationEmail(String to, String rawToken) {
        sentEmails.add(new SentEmail(to, EmailType.VERIFICATION, rawToken));
        log.info("[AUTH EMAIL - DEV] VERIFICAÇÃO para: {} | Token: {} | Link: {}",
            to, rawToken,
            "http://localhost:4200/verify-email?token=" + rawToken);
    }

    @Override
    public void sendPasswordResetEmail(String to, String rawToken) {
        sentEmails.add(new SentEmail(to, EmailType.PASSWORD_RESET, rawToken));
        log.info("[AUTH EMAIL - DEV] RESET DE SENHA para: {} | Token: {} | Link: {}",
            to, rawToken,
            "http://localhost:4200/reset-password?token=" + rawToken);
    }

    @Override
    public void sendPasswordResetConfirmationEmail(String to) {
        sentEmails.add(new SentEmail(to, EmailType.PASSWORD_RESET_CONFIRMATION, null));
        log.info("[AUTH EMAIL - DEV] CONFIRMAÇÃO RESET para: {}", to);
    }

    public List<SentEmail> getSentEmails() {
        return Collections.unmodifiableList(sentEmails);
    }

    public void clear() {
        sentEmails.clear();
    }

    public String getLastTokenFor(String email, EmailType type) {
        return sentEmails.stream()
                .filter(e -> e.to().equals(email) && e.type() == type)
                .reduce((first, second) -> second)
                .map(SentEmail::token)
                .orElseThrow(() -> new IllegalStateException(
                    "Nenhum e-mail do tipo " + type + " encontrado para " + email));
    }
}