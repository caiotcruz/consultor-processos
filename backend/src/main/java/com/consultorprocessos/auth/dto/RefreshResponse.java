package com.consultorprocessos.auth.dto;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        int    expiresIn,
        String tokenType
) {}