package com.consultorprocessos.user.controller;

import com.consultorprocessos.auth.controller.AuthController.MessageResponse;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.shared.response.ApiResponse;
import com.consultorprocessos.user.dto.*;
import com.consultorprocessos.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UserProfileResponse profile = userService.getProfile(principal);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody @Valid UpdateProfileRequest request) {

        UserProfileResponse updated = userService.updateProfile(principal, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<MessageResponse>> changePassword(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody @Valid ChangePasswordRequest request) {

        userService.changePassword(principal, request);
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse(
            "Senha alterada com sucesso. Todas as sessões foram encerradas.")));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<MessageResponse>> deleteAccount(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody @Valid DeleteAccountRequest request) {

        userService.deleteAccount(principal, request);
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse(
            "Conta excluída. Seus dados pessoais foram removidos.")));
    }
}