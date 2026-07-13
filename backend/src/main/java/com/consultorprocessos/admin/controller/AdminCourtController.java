package com.consultorprocessos.admin.controller;

import com.consultorprocessos.admin.dto.*;
import com.consultorprocessos.admin.service.AdminCourtService;
import com.consultorprocessos.auth.controller.AuthController.MessageResponse;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/courts")
@RequiredArgsConstructor
public class AdminCourtController {

    private final AdminCourtService adminCourtService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminCourtResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(adminCourtService.listAllCourts()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<AdminCourtResponse>> getByCode(
            @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(adminCourtService.getCourtByCode(code)));
    }

    @PatchMapping("/{code}")
    public ResponseEntity<ApiResponse<AdminCourtResponse>> updateCourt(
            @PathVariable String code,
            @RequestBody @Valid AdminCourtUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminCourtService.updateCourt(code, request)));
    }

    @PatchMapping("/{courtId}/flags/{flagKey}")
    public ResponseEntity<ApiResponse<MessageResponse>> updateFlag(
            @PathVariable UUID   courtId,
            @PathVariable String flagKey,
            @RequestParam boolean enabled,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        adminCourtService.updateFeatureFlag(courtId, flagKey, enabled,
                principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse(
                "Feature flag '" + flagKey + "' atualizada para " + enabled + ".")));
    }
}