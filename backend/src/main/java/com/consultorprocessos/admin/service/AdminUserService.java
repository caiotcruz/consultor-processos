package com.consultorprocessos.admin.service;

import com.consultorprocessos.admin.annotation.Audited;
import com.consultorprocessos.admin.dto.AdminUserResponse;
import com.consultorprocessos.admin.dto.AdminUserUpdateRequest;
import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.entity.UserStatus;
import com.consultorprocessos.auth.repository.UserRepository;
import com.consultorprocessos.plan.repository.PlanRepository;
import com.consultorprocessos.plan.service.PlanService;
import com.consultorprocessos.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository  userRepository;
    private final PlanRepository  planRepository;
    private final PlanService     planService;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String status, Pageable pageable) {
        Page<User> users = (status != null)
                ? userRepository.findByStatusOrderByCreatedAtDesc(
                        UserStatus.valueOf(status), pageable)
                : userRepository.findAllByOrderByCreatedAtDesc(pageable);

        return users.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));
    }

    @Audited(action = "UPDATE_USER", entityType = "USER")
    @Transactional
    public AdminUserResponse updateUser(UUID userId, AdminUserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));

        if (StringUtils.hasText(request.status())) {
            UserStatus newStatus = UserStatus.valueOf(request.status());
            log.info("Admin: alterando status do usuário {} para {}", userId, newStatus);
            user.setStatus(newStatus);
        }

        if (StringUtils.hasText(request.plan())) {
            planRepository.findByName(request.plan().toUpperCase())
                    .ifPresent(plan -> {
                        log.info("Admin: alterando plano do usuário {} para {}",
                                userId, plan.getName());
                        user.setPlan(plan);
                    });
        }

        userRepository.save(user);
        return toResponse(user);
    }

    private AdminUserResponse toResponse(User user) {
        int activeProcesses = (int) planService.countActiveSubscriptions(user.getId());
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getStatus().name(),
                user.getPlan().getName(),
                activeProcesses,
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}