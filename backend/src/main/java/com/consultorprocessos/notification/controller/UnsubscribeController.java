// src/main/java/com/consultorprocessos/notification/controller/UnsubscribeController.java
package com.consultorprocessos.notification.controller;

import com.consultorprocessos.auth.repository.UserRepository;
import com.consultorprocessos.notification.service.UnsubscribeTokenService;
import com.consultorprocessos.user.entity.UserNotificationPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/unsubscribe")
@RequiredArgsConstructor
@Slf4j
public class UnsubscribeController {

    private final UnsubscribeTokenService unsubscribeTokenService;
    private final UserRepository          userRepository;

    @GetMapping
    public ResponseEntity<String> unsubscribe(
            @RequestParam("uid") UUID   userId,
            @RequestParam("sig") String signature) {

        if (!unsubscribeTokenService.validate(userId, signature)) {
            log.warn("Tentativa de descadastro com assinatura inválida: userId={}", userId);
            return ResponseEntity.badRequest()
                    .body("Link de descadastro inválido ou expirado.");
        }

        userRepository.findById(userId).ifPresent(user -> {
            UserNotificationPreferences prefs = user.getNotificationPreferences();
            prefs.setEmailEnabled(false);
            userRepository.save(user);
            log.info("Notificações de e-mail desativadas via link: userId={}", userId);
        });

        return ResponseEntity.ok(
                "Notificações por e-mail desativadas com sucesso. " +
                "Você pode reativá-las a qualquer momento nas configurações do sistema.");
    }
}