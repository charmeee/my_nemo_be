package com.nemo.nemo.domain.auth.dto;

public record MemberResponse(
        String id,
        String email,
        String nickname,
        String profileImage
) {}
