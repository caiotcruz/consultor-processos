package com.consultorprocessos.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Slf4j
public class UnsubscribeTokenService {

    @Value("${app.notifications.unsubscribe-secret}")
    private String secret;

    public String generate(UUID userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(userId.toString().getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar token de descadastro", e);
        }
    }

    public boolean validate(UUID userId, String token) {
        try {
            String expected = generate(userId);
            return constantTimeEquals(expected, token);
        } catch (Exception e) {
            log.warn("Falha ao validar token de descadastro: {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public String buildUrl(String unsubscribeBaseUrl, UUID userId) {
        return unsubscribeBaseUrl + "?uid=" + userId + "&sig=" + generate(userId);
    }
}