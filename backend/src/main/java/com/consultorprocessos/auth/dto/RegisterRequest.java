package com.consultorprocessos.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Nome é obrigatório.")
        @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres.")
        String name,

        @NotBlank(message = "E-mail é obrigatório.")
        @Email(message = "Formato de e-mail inválido.")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Senha é obrigatória.")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres.")
        @Pattern(regexp = ".*\\d.*", message = "Senha deve conter pelo menos 1 número.")
        String password
) {}