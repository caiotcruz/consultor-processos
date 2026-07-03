package com.consultorprocessos.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres.")
        String name,

        @Valid
        NotificationPrefsUpdate notifications
) {
    public record NotificationPrefsUpdate(
            Boolean emailEnabled,
            Boolean pushEnabled
    ) {}
}