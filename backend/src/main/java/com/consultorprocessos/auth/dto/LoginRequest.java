package com.consultorprocessos.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "E-mail é obrigatório.")
        @Email
        String email,

        @NotBlank(message = "Senha é obrigatória.")
        String password
) {}