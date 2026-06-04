package com.nemo.nemo.domain.album.dto;

public record AlbumUpdateRequest(
        String name,
        String coverImage,
        Boolean isLocked
) {}
