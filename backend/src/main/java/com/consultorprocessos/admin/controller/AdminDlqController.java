package com.consultorprocessos.admin.controller;

import com.consultorprocessos.admin.dto.DlqMessageResponse;
import com.consultorprocessos.admin.service.AdminDlqService;
import com.consultorprocessos.auth.controller.AuthController.MessageResponse;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/dlq")
@RequiredArgsConstructor
public class AdminDlqController {

    private final AdminDlqService adminDlqService;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<DlqMessageResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminDlqService.listPending(pageable)));
    }

    @PostMapping("/{id}/requeue")
    public ResponseEntity<ApiResponse<MessageResponse>> requeue(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        adminDlqService.requeue(id, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                new MessageResponse("Mensagem requeueada com sucesso.")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<MessageResponse>> discard(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        adminDlqService.discard(id, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                new MessageResponse("Mensagem descartada.")));
    }

    @PostMapping("/requeue-all")
    public ResponseEntity<ApiResponse<MessageResponse>> requeueAll(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        int count = adminDlqService.requeueAll(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                new MessageResponse(count + " mensagem(ns) requeueada(s).")));
    }
}