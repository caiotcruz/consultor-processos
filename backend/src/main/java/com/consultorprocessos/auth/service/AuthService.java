// src/main/java/com/consultorprocessos/auth/service/AuthService.java
package com.consultorprocessos.auth.service;

import com.consultorprocessos.auth.dto.*;
import com.consultorprocessos.auth.entity.*;
import com.consultorprocessos.auth.exception.*;
import com.consultorprocessos.auth.repository.*;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.plan.repository.PlanRepository;
import com.consultorprocessos.shared.config.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PlanRepository         planRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtService             jwtService;
    private final AuthEmailService       emailService;

    @Value("${app.jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new EmailAlreadyExistsException();
        }

        var plan = planRepository.findByName("GRATUITO")
                .orElseThrow(() -> new IllegalStateException(
                        "Plano GRATUITO não encontrado. Verifique o seed V100."));

        User user = new User();
        user.setName(request.name().strip());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPlan(plan);
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setRoles(Set.of("ROLE_USER"));
        userRepository.save(user);

        String rawToken = generateSecureToken();
        emailService.sendVerificationEmail(user.getEmail(), rawToken);
        saveVerificationToken(user, rawToken, 24);

        log.info("Usuário registrado: userId={}", user.getId());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String hash = sha256(rawToken);

        PasswordReset tokenEntity = passwordResetRepository.findByTokenHash(hash)
                .orElseThrow(InvalidTokenException::new);

        if (!tokenEntity.isValid()) {
            throw new InvalidTokenException();
        }

        User user = tokenEntity.getUser();
        if (user.isEmailVerified()) {
            return;
        }

        user.setEmailVerifiedAt(Instant.now());
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        tokenEntity.markAsUsed();
        passwordResetRepository.save(tokenEntity);

        log.info("E-mail verificado: userId={}", user.getId());
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.isLocked()) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.incrementLoginFailures();
            userRepository.save(user);
            if (user.isLocked()) {
                throw new AccountLockedException(user.getLockedUntil());
            }
            throw new InvalidCredentialsException();
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        if (UserStatus.SUSPENDED.equals(user.getStatus())) {
            throw new AccountSuspendedException();
        }
        if (UserStatus.DELETED.equals(user.getStatus())) {
            throw new InvalidCredentialsException();
        }

        user.resetLoginFailures();
        userRepository.save(user);

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String accessToken          = jwtService.generateAccessToken(userDetails);
        String rawRefreshToken      = generateSecureToken();
        saveRefreshToken(user, rawRefreshToken, httpRequest);

        log.info("Login realizado: userId={}", user.getId());

        return new LoginResponse(
            accessToken,
            rawRefreshToken,
            900,
            "Bearer",
            new LoginResponse.UserSummary(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPlan().getName(),
                user.getPlan().getDisplayName()
            )
        );
    }

    @Transactional
    public RefreshResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);

        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidTokenException::new);

        if (!tokenEntity.isValid()) {
            throw new InvalidTokenException();
        }

        User user = tokenEntity.getUser();
        if (!user.isActive() || user.isLocked()) {
            tokenEntity.revoke();
            refreshTokenRepository.save(tokenEntity);
            throw new InvalidTokenException();
        }

        tokenEntity.revoke();
        refreshTokenRepository.save(tokenEntity);

        UserDetailsImpl userDetails    = new UserDetailsImpl(user);
        String          newAccessToken = jwtService.generateAccessToken(userDetails);
        String          newRawRefresh  = generateSecureToken();
        saveRefreshToken(user, newRawRefresh, null);

        log.debug("Refresh token rotacionado: userId={}", user.getId());

        return new RefreshResponse(newAccessToken, newRawRefresh, 900, "Bearer");
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email.toLowerCase())
                .filter(User::isActive)
                .ifPresent(user -> {
                    passwordResetRepository.invalidateAllByUserId(user.getId());

                    String rawToken = generateSecureToken();
                    savePasswordResetToken(user, rawToken, 1); // 1 hora
                    emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
                    log.info("Password reset solicitado: userId={}", user.getId());
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = sha256(request.token());

        PasswordReset tokenEntity = passwordResetRepository.findByTokenHash(hash)
                .orElseThrow(InvalidTokenException::new);

        if (!tokenEntity.isValid()) {
            throw new InvalidTokenException();
        }

        User user = tokenEntity.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        tokenEntity.markAsUsed();
        passwordResetRepository.save(tokenEntity);

        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());

        emailService.sendPasswordResetConfirmationEmail(user.getEmail());
        log.info("Senha redefinida: userId={}", user.getId());
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email.toLowerCase())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    passwordResetRepository.invalidateAllByUserId(user.getId());

                    String rawToken = generateSecureToken();
                    saveVerificationToken(user, rawToken, 24);
                    emailService.sendVerificationEmail(user.getEmail(), rawToken);
                    log.info("Verificação reenviada: userId={}", user.getId());
                });
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private void saveVerificationToken(User user, String rawToken, int expiryHours) {
        PasswordReset pr = new PasswordReset();
        pr.setUser(user);
        pr.setTokenHash(sha256(rawToken));
        pr.setExpiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS));
        passwordResetRepository.save(pr);
    }

    private void savePasswordResetToken(User user, String rawToken, int expiryHours) {
        saveVerificationToken(user, rawToken, expiryHours);
    }

    private void saveRefreshToken(User user, String rawToken,
                                  HttpServletRequest httpRequest) {
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(sha256(rawToken));
        rt.setExpiresAt(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS));
        if (httpRequest != null) {
            rt.setUserAgent(truncate(httpRequest.getHeader("User-Agent"), 512));
            rt.setIpAddress(httpRequest.getRemoteAddr());
        }
        refreshTokenRepository.save(rt);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}