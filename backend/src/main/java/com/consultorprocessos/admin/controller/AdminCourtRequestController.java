package com.consultorprocessos.admin.controller;

import com.consultorprocessos.admin.annotation.Audited;
import com.consultorprocessos.admin.dto.*;
import com.consultorprocessos.court.entity.CourtRequest;
import com.consultorprocessos.court.entity.CourtRequestStatus;
import com.consultorprocessos.court.repository.CourtRequestRepository;
import com.consultorprocessos.shared.exception.NotFoundException;
import com.consultorprocessos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/court-requests")
@RequiredArgsConstructor
public class AdminCourtRequestController {

    private final CourtRequestRepository courtRequestRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<AdminCourtRequestResponse>>> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt",
                            direction = Sort.Direction.DESC) Pageable pageable) {

        Page<CourtRequest> page = (status != null)
                ? courtRequestRepository.findByStatusOrderByCreatedAtDesc(
                        CourtRequestStatus.valueOf(status.toUpperCase()), pageable)
                : courtRequestRepository.findAllByOrderByCreatedAtDesc(pageable);

        return ResponseEntity.ok(ApiResponse.success(page.map(this::toResponse)));
    }

    @Audited(action = "UPDATE_COURT_REQUEST", entityType = "COURT_REQUEST")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCourtRequestResponse>> update(
            @PathVariable UUID id,
            @RequestBody @Valid AdminCourtRequestUpdateRequest request) {

        CourtRequest req = courtRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Solicitação não encontrada: " + id));

        req.setStatus(CourtRequestStatus.valueOf(request.status()));
        if (StringUtils.hasText(request.adminNotes())) {
            req.setAdminNotes(request.adminNotes());
        }
        courtRequestRepository.save(req);

        return ResponseEntity.ok(ApiResponse.success(toResponse(req)));
    }

    private AdminCourtRequestResponse toResponse(CourtRequest req) {
        String requesterEmail = req.getUser() != null ? req.getUser().getEmail() : null;
        return new AdminCourtRequestResponse(
                req.getId(), req.getCourtName(), req.getCourtCode(),
                req.getProcessNumber(), req.getStatus().name(),
                req.getAdminNotes(), requesterEmail, req.getCreatedAt()
        );
    }
}