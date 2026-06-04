package com.nemo.nemo.domain.album.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.album.dto.MemberResponse;
import com.nemo.nemo.domain.album.dto.RoleChangeRequest;
import com.nemo.nemo.domain.album.dto.TransferAdminRequest;
import com.nemo.nemo.domain.album.service.AlbumMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AlbumMemberController {

    private final AlbumMemberService albumMemberService;

    @GetMapping("/albums/{albumId}/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(
            @PathVariable UUID albumId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal String userId) {
        UUID requesterId = UUID.fromString(userId);
        List<MemberResponse> members = "pending".equalsIgnoreCase(status)
                ? albumMemberService.getPendingMembers(albumId, requesterId)
                : albumMemberService.getMembers(albumId, requesterId);
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @PostMapping("/albums/{albumId}/members/{targetUserId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveMember(
            @PathVariable UUID albumId,
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal String userId) {
        albumMemberService.approveMember(albumId, targetUserId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/albums/{albumId}/members/{targetUserId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectMember(
            @PathVariable UUID albumId,
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal String userId) {
        albumMemberService.rejectMember(albumId, targetUserId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/albums/{albumId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> changeRole(
            @PathVariable UUID albumId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody RoleChangeRequest req,
            @AuthenticationPrincipal String userId) {
        albumMemberService.changeRole(albumId, targetUserId, req.role(), UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/albums/{albumId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(
            @PathVariable UUID albumId,
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal String userId) {
        albumMemberService.kickMember(albumId, targetUserId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/albums/{albumId}/members/me")
    public ResponseEntity<ApiResponse<Void>> leaveAlbum(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        albumMemberService.leaveAlbum(albumId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/albums/{albumId}/members/transfer")
    public ResponseEntity<ApiResponse<Void>> transferAdmin(
            @PathVariable UUID albumId,
            @Valid @RequestBody TransferAdminRequest req,
            @AuthenticationPrincipal String userId) {
        albumMemberService.transferAdmin(albumId, UUID.fromString(req.targetUserId()), UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
