package com.consultorprocessos.admin.controller;

import com.consultorprocessos.admin.dto.*;
import com.consultorprocessos.admin.service.AdminUserService;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<AdminUserResponse>>> listUsers( 
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt",
                            direction = Sort.Direction.DESC) Pageable pageable) {
        
        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.listUsers(status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUser(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @PathVariable UUID id,
            @RequestBody @Valid AdminUserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.updateUser(id, request)));
    }
}