package com.nemo.nemo.domain.album.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlbumCreateRequest(
        @NotBlank @Size(max = 30) String name,
        String coverImage
) {}
