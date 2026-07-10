package com.consultorprocessos.notification.controller;

import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.notification.dto.NotificationHistoryResponse;
import com.consultorprocessos.notification.entity.NotificationHistory;
import com.consultorprocessos.notification.repository.NotificationHistoryRepository;
import com.consultorprocessos.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users/me/notifications")
@RequiredArgsConstructor
public class NotificationHistoryController {

    private final NotificationHistoryRepository repository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationHistoryResponse>>> list(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PageableDefault(size = 20, sort = "sentAt",
                            direction = Sort.Direction.DESC) Pageable pageable) {

        Page<NotificationHistoryResponse> page = repository
                .findByUserIdOrderBySentAtDesc(principal.getUserId(), pageable)
                .map(this::toResponse);

        return ResponseEntity.ok(ApiResponse.success(page));
    }

    private NotificationHistoryResponse toResponse(NotificationHistory h) {
        String processNumber = h.getProcess() != null
                ? h.getProcess().getProcessNumber()
                : null;

        return new NotificationHistoryResponse(
                h.getId(),
                h.getChannel(),
                h.getEventType(),
                h.getStatus(),
                h.getErrorMessage(),
                h.getSentAt(),
                processNumber
        );
    }
}