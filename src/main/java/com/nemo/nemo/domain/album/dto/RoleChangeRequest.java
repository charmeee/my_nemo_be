package com.nemo.nemo.domain.album.dto;

import com.nemo.nemo.domain.album.entity.AlbumRole;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(
        @NotNull AlbumRole role
) {}
