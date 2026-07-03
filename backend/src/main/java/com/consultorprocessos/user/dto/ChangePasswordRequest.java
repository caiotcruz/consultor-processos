package com.consultorprocessos.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Senha atual é obrigatória.")
        String currentPassword,

        @NotBlank(message = "Nova senha é obrigatória.")
        @Size(min = 8, message = "A nova senha deve ter no mínimo 8 caracteres.")
        @Pattern(regexp = ".*\\d.*", message = "A nova senha deve conter pelo menos 1 número.")
        String newPassword
) {}