package com.consultorprocessos.shared.config;

import com.consultorprocessos.auth.entity.UserStatus;
import com.consultorprocessos.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataInitializer implements ApplicationRunner {

    private static final String DEV_EMAIL    = "dev@consultorprocessos.com.br";
    private static final String DEV_PASSWORD = "Dev@123!";

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByEmail(DEV_EMAIL).ifPresent(user -> {
            user.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerifiedAt(Instant.now());
            user.setLoginFailureCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            log.info("┌─────────────────────────────────────────────────────────┐");
            log.info("│  [DEV] Usuário de desenvolvimento configurado           │");
            log.info("│  E-mail : {}               │", DEV_EMAIL);
            log.info("│  Senha  : {}                             │", DEV_PASSWORD);
            log.info("│  DevModeFilter: autenticação automática ativa           │");
            log.info("└─────────────────────────────────────────────────────────┘");
        });
    }
}