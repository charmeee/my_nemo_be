package com.nemo.nemo.domain.album.dto;

public record AlbumResponse(
        String id,
        String name,
        String coverImage,
        boolean isLocked,
        String createdAt,
        String updatedAt,
        int memberCount,
        String myRole
) {}
