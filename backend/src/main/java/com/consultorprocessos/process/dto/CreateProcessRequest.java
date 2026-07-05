package com.consultorprocessos.process.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProcessRequest(
        @NotBlank(message = "Número do processo é obrigatório.")
        @Size(max = 50, message = "Número do processo muito longo.")
        String processNumber,

        @NotBlank(message = "Código do tribunal é obrigatório.")
        @Size(max = 20, message = "Código do tribunal inválido.")
        String courtCode,

        @Size(max = 200, message = "Alias não pode ter mais de 200 caracteres.")
        String alias
) {}