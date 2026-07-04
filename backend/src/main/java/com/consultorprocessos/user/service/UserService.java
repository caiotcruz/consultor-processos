package com.consultorprocessos.user.service;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.entity.UserStatus;
import com.consultorprocessos.auth.repository.RefreshTokenRepository;
import com.consultorprocessos.auth.repository.UserRepository;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.plan.service.PlanService;
import com.consultorprocessos.process.repository.ProcessSubscriptionRepository;
import com.consultorprocessos.user.dto.*;
import com.consultorprocessos.user.entity.UserNotificationPreferences;
import com.consultorprocessos.user.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String CONFIRM_PHRASE = "DELETAR MINHA CONTA";

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlanService            planService;
    private final PasswordEncoder        passwordEncoder;
    private final ProcessSubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UserDetailsImpl principal) {
        User user = loadUser(principal.getUserId());
        return buildProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UserDetailsImpl principal,
                                             UpdateProfileRequest request) {
        User user = loadUser(principal.getUserId());
        boolean changed = false;

        if (StringUtils.hasText(request.name())) {
            user.setName(request.name().strip());
            changed = true;
        }

        if (request.notifications() != null) {
            UserNotificationPreferences prefs = user.getNotificationPreferences();

            if (request.notifications().emailEnabled() != null) {
                prefs.setEmailEnabled(request.notifications().emailEnabled());
                changed = true;
            }
            if (request.notifications().pushEnabled() != null) {
                prefs.setPushEnabled(request.notifications().pushEnabled());
                changed = true;
            }
        }

        if (changed) {
            userRepository.save(user);
            log.info("Perfil atualizado: userId={}", user.getId());
        }

        return buildProfileResponse(user);
    }

    @Transactional
    public void changePassword(UserDetailsImpl principal, ChangePasswordRequest request) {
        User user = loadUser(principal.getUserId());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new SamePasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());

        log.info("Senha alterada: userId={}. Todos os refresh tokens revogados.", user.getId());
    }

    @Transactional
    public void deleteAccount(UserDetailsImpl principal, DeleteAccountRequest request) {
        User user = loadUser(principal.getUserId());

        if (!CONFIRM_PHRASE.equals(request.confirmPhrase())) {
            throw new InvalidConfirmPhraseException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        int deactivated = subscriptionRepository.deactivateAllByUserId(user.getId(), Instant.now());

        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());

        user.anonymize();
        userRepository.save(user);

        log.info("Conta excluída: userId={}. Subscriptions desativadas: {}",
            user.getId(), deactivated);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !UserStatus.DELETED.equals(u.getStatus()))
                .orElseThrow(() -> new IllegalStateException(
                        "Usuário não encontrado ou deletado: " + userId));
    }

    private UserProfileResponse buildProfileResponse(User user) {
        int     activeCount    = planService.countActiveSubscriptions(user.getId());
        Integer remaining      = planService.getRemainingCapacity(user);

        return new UserProfileResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getStatus().name(),
            new UserProfileResponse.PlanInfo(
                user.getPlan().getName(),
                user.getPlan().getDisplayName(),
                user.getPlan().getMaxProcesses(),
                user.getPlan().getCheckIntervalHours()
            ),
            new UserProfileResponse.UsageInfo(
                activeCount,
                remaining
            ),
            new UserProfileResponse.NotificationInfo(
                user.getNotificationPreferences().isEmailEnabled(),
                user.getNotificationPreferences().isPushEnabled()
            ),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }
}