package com.nemo.nemo.domain.album.dto;

import java.util.List;

public record AlbumResponse(
        String id,
        String name,
        String coverImage,
        boolean isLocked,
        String createdAt,
        String updatedAt,
        int memberCount,
        String myRole,
        List<MemberAvatar> recentMembers
) {
    public record MemberAvatar(String nickname, String profileImage) {}
}
