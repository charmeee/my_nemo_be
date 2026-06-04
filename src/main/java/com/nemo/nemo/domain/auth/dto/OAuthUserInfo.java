package com.nemo.nemo.domain.auth.dto;

import com.nemo.nemo.domain.member.entity.AuthProvider;

public record OAuthUserInfo(
        String providerId,
        String email,
        String nickname,
        String profileImage,
        AuthProvider provider
) {}
