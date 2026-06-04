package com.nemo.nemo.domain.album.dto;

public record MemberResponse(
        String id,
        String userId,
        String nickname,
        String profileImage,
        String role,
        String status,
        String joinedAt
) {}
