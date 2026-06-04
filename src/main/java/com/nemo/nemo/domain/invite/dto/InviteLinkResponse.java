package com.nemo.nemo.domain.invite.dto;

public record InviteLinkResponse(String id, String code, String role, boolean approvalRequired, boolean isActive) {}
