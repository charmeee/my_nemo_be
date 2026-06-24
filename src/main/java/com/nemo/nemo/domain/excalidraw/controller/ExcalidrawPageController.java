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

    // 앨범의 페이지 목록 조회 (멤버 전용)
    @GetMapping
    public ResponseEntity<ApiResponse<List<PageListResponse>>> getPages(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                pageService.getPages(albumId, UUID.fromString(userId))));
    }

    // 새 페이지 생성 (EDITOR 이상)
    @PostMapping
    public ResponseEntity<ApiResponse<PageListResponse>> createPage(
            @PathVariable UUID albumId,
            @AuthenticationPrincipal String userId,
            @RequestBody PageCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                pageService.createPage(albumId, UUID.fromString(userId), req)));
    }

    // 페이지 이름/순서 수정 (EDITOR 이상)
    @PatchMapping("/{pageId}")
    public ResponseEntity<ApiResponse<PageListResponse>> updatePage(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal String userId,
            @RequestBody PageUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                pageService.updatePage(albumId, pageId, UUID.fromString(userId), req)));
    }

    // 페이지의 현재 elements + 이미지 파일 매핑 조회 (메모리→Redis→DB 순)
    @GetMapping("/{pageId}/elements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPageElements(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                pageService.getPageElements(albumId, pageId, UUID.fromString(userId))));
    }

    // 페이지 soft delete + 휴지통 이동 (EDITOR 이상)
    @DeleteMapping("/{pageId}")
    public ResponseEntity<ApiResponse<Void>> deletePage(
            @PathVariable UUID albumId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal String userId) {
        pageService.deletePage(albumId, pageId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
