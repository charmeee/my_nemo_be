package com.nemo.nemo.domain.page.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.page.service.AlbumPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/albums/{albumId}/pages")
@RequiredArgsConstructor
public class AlbumPageController {

    private final AlbumPageService albumPageService;

    @PostMapping("/{pageId}/thumbnail")
    public ResponseEntity<ApiResponse<Void>> uploadThumbnail(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal String userId) {
        albumPageService.uploadThumbnail(albumId, pageId, file, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }


}
