package com.nemo.nemo.domain.image.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.image.dto.ImageResponse;
import com.nemo.nemo.domain.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/albums/{albumId}/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping
    public ResponseEntity<ApiResponse<ImageResponse>> uploadImage(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(
                imageService.uploadImage(albumId, UUID.fromString(userId), file)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ImageResponse>>> getImages(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                imageService.getImages(albumId, UUID.fromString(userId))));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable UUID albumId,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal String userId) {
        imageService.deleteImage(albumId, imageId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
