package com.nemo.nemo.domain.album.dto;

import java.util.List;

public record AlbumListResponse(
        List<AlbumResponse> owned,
        List<AlbumResponse> joined
) {}
