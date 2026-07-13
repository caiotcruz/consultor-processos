package com.consultorprocessos.admin.controller;

import com.consultorprocessos.admin.dto.AuditLogResponse;
import com.consultorprocessos.admin.entity.AuditLog;
import com.consultorprocessos.admin.repository.AuditLogRepository;
import com.consultorprocessos.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<AuditLogResponse>>> list(
            @PageableDefault(size = 30, sort = "createdAt",
                            direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AuditLogResponse> page = auditLogRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);

        return ResponseEntity.ok(ApiResponse.success(page));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getActorEmail(), log.getAction(),
                log.getEntityType(), log.getEntityId(),
                log.getOldValue(), log.getNewValue(),
                log.getIpAddress(), log.getCreatedAt()
        );
    }
}