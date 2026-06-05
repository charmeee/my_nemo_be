package com.nemo.nemo.domain.invite.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.invite.dto.InviteCreateRequest;
import com.nemo.nemo.domain.invite.dto.InviteInfoResponse;
import com.nemo.nemo.domain.invite.dto.InviteLinkResponse;
import com.nemo.nemo.domain.invite.service.InviteService;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/albums/{albumId}/invite")
    public ResponseEntity<ApiResponse<List<InviteLinkResponse>>> getInviteLinks(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                inviteService.getInviteLinks(albumId, UUID.fromString(userId))));
    }

    @PostMapping("/albums/{albumId}/invite")
    public ResponseEntity<ApiResponse<InviteLinkResponse>> createInviteLink(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId,
            @RequestBody InviteCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                inviteService.createInviteLink(albumId, UUID.fromString(userId), req)));
    }

    @GetMapping("/invite/{code}/info")
    public ResponseEntity<ApiResponse<InviteInfoResponse>> getInviteInfo(
            @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(inviteService.getInviteInfo(code)));
    }

    @PostMapping("/invite/{code}/join")
    public ResponseEntity<ApiResponse<Void>> joinViaInvite(
            @PathVariable String code,
            @AuthenticationPrincipal String userId) {
        inviteService.joinViaInvite(code, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/albums/{albumId}/invite/reissue")
    public ResponseEntity<ApiResponse<InviteLinkResponse>> reissueInviteLink(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                inviteService.reissueInviteLink(albumId, UUID.fromString(userId))));
    }

    @PatchMapping("/albums/{albumId}/invite/{linkId}")
    public ResponseEntity<ApiResponse<Void>> toggleInviteLink(
            @PathVariable UUID albumId,
            @PathVariable UUID linkId,
            @AuthenticationPrincipal String userId,
            @RequestParam boolean active) {
        inviteService.toggleInviteLink(linkId, UUID.fromString(userId), active);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
