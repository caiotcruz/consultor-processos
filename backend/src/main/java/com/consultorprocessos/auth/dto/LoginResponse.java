package com.consultorprocessos.auth.dto;

import java.util.UUID;

public record LoginResponse(
        String      accessToken,
        String      refreshToken,
        int         expiresIn,
        String      tokenType,
        UserSummary user
) {
    public record UserSummary(
            UUID   id,
            String name,
            String email,
            String plan,
            String planDisplay
    ) {}
}