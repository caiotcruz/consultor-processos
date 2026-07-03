package com.consultorprocessos.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "Token é obrigatório.")
        String token
) {}