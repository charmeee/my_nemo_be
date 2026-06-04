package com.nemo.nemo.domain.auth.dto;

public record RefreshResponse(
        String accessToken,
        long expiresIn
) {}
