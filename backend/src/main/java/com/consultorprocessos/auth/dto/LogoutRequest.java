package com.consultorprocessos.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token é obrigatório.")
        String refreshToken
) {}