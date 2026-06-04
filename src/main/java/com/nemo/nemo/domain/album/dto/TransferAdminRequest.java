package com.nemo.nemo.domain.album.dto;

import jakarta.validation.constraints.NotNull;

public record TransferAdminRequest(
        @NotNull String targetUserId
) {}
