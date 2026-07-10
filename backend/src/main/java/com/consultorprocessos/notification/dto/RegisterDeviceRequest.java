package com.consultorprocessos.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank(message = "Token FCM é obrigatório.")
        @Size(max = 4096, message = "Token FCM inválido.")
        String token,

        @NotBlank(message = "Plataforma é obrigatória.")
        @Pattern(regexp = "ANDROID|IOS|WEB",
                 message = "Plataforma deve ser ANDROID, IOS ou WEB.")
        String platform
) {}