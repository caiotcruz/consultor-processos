package com.consultorprocessos.auth.dto;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
        @NotBlank(message = "Token é obrigatório.")
        String token,

        @NotBlank(message = "Nova senha é obrigatória.")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres.")
        @Pattern(regexp = ".*\\d.*", message = "Senha deve conter pelo menos 1 número.")
        String newPassword
) {}