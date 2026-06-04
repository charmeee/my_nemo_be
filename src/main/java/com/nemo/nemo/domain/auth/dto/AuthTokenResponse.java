package com.nemo.nemo.domain.auth.dto;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
