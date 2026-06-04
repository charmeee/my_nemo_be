package com.nemo.nemo.domain.invite.dto;

import com.nemo.nemo.domain.album.entity.AlbumRole;

public record InviteCreateRequest(AlbumRole role, boolean approvalRequired) {}
