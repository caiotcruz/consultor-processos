package com.consultorprocessos.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Senha é obrigatória para confirmar a exclusão.")
        String password,

        @NotBlank(message = "Frase de confirmação é obrigatória.")
        String confirmPhrase
) {}