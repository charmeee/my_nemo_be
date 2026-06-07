package com.nemo.nemo.domain.excalidraw.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.excalidraw.dto.PageCreateRequest;
import com.nemo.nemo.domain.excalidraw.dto.PageListResponse;
import com.nemo.nemo.domain.excalidraw.dto.PageUpdateRequest;
import com.nemo.nemo.domain.excalidraw.service.ExcalidrawPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/albums/{albumId}/pages")
@RequiredArgsConstructor
public class ExcalidrawPageController {

    private final ExcalidrawPageService pageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PageListResponse>>> getPages(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                pageService.getPages(albumId, UUID.fromString(userId))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PageListResponse>> createPage(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId,
            @RequestBody PageCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                pageService.createPage(albumId, UUID.fromString(userId), req)));
    }

    @PatchMapping("/{pageId}")
    public ResponseEntity<ApiResponse<PageListResponse>> updatePage(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal String userId,
            @RequestBody PageUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                pageService.updatePage(albumId, pageId, UUID.fromString(userId), req)));
    }

    @GetMapping("/{pageId}/elements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPageElements(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal String userId) {
        List<Object> elements = pageService.getPageElements(albumId, pageId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("elements", elements)));
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<ApiResponse<Void>> deletePage(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal String userId) {
        pageService.deletePage(albumId, pageId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
