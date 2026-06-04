package com.nemo.nemo.domain.page.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.page.dto.PageThumbnailResponse;
import com.nemo.nemo.domain.page.service.AlbumPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/albums/{albumId}/pages")
@RequiredArgsConstructor
public class AlbumPageController {

    private final AlbumPageService albumPageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PageThumbnailResponse>>> getPages(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(albumPageService.getPages(albumId, UUID.fromString(userId))));
    }

    @PostMapping("/{pageId}/thumbnail")
    public ResponseEntity<ApiResponse<Void>> uploadThumbnail(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal String userId) {
        albumPageService.uploadThumbnail(albumId, pageId, file, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<ApiResponse<Void>> deletePage(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal String userId) {
        albumPageService.deletePage(albumId, pageId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
