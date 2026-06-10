package com.nemo.nemo.domain.album.dto;

import com.nemo.nemo.domain.album.entity.AlbumRole;

public record DirectInviteRequest(String email, AlbumRole role) {}
