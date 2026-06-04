package com.nemo.nemo.domain.album.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.album.dto.*;
import com.nemo.nemo.domain.album.service.AlbumService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @GetMapping
    public ResponseEntity<ApiResponse<AlbumListResponse>> getMyAlbums(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(albumService.getMyAlbums(UUID.fromString(userId))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AlbumResponse>> createAlbum(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AlbumCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(albumService.createAlbum(UUID.fromString(userId), req)));
    }

    @GetMapping("/{albumId}")
    public ResponseEntity<ApiResponse<AlbumResponse>> getAlbum(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(albumService.getAlbum(albumId, UUID.fromString(userId))));
    }

    @PatchMapping("/{albumId}")
    public ResponseEntity<ApiResponse<AlbumResponse>> updateAlbum(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId,
            @RequestBody AlbumUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(albumService.updateAlbum(albumId, UUID.fromString(userId), req)));
    }

    @DeleteMapping("/{albumId}")
    public ResponseEntity<ApiResponse<Void>> deleteAlbum(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        albumService.deleteAlbum(albumId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
