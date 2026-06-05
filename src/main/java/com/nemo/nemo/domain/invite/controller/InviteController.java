package com.nemo.nemo.domain.invite.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.excalidraw.dto.PageListResponse;
import com.nemo.nemo.domain.excalidraw.service.ExcalidrawPageService;
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
    private final ExcalidrawPageService excalidrawPageService;

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
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> joinViaInvite(
            @PathVariable String code,
            @AuthenticationPrincipal String userId) {
        var status = inviteService.joinViaInvite(code, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("status", status.name())));
    }

    @PostMapping("/albums/{albumId}/invite/reissue")
    public ResponseEntity<ApiResponse<InviteLinkResponse>> reissueInviteLink(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                inviteService.reissueInviteLink(albumId, UUID.fromString(userId))));
    }

    /** N-CORE-13: 게스트가 페이지 목록을 조회하는 공개 엔드포인트 (멤버십 검증 없음) */
    @GetMapping("/invite/{code}/pages")
    public ResponseEntity<ApiResponse<List<PageListResponse>>> getGuestPages(
            @PathVariable String code) {
        var albumId = inviteService.getAlbumIdByCode(code);
        return ResponseEntity.ok(ApiResponse.ok(excalidrawPageService.getPublicPages(albumId)));
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
