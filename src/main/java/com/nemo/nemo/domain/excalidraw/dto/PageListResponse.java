package com.nemo.nemo.domain.excalidraw.dto;

public record PageListResponse(
        String pageId,
        String name,
        int pageOrder,
        String thumbnailUrl,
        String createdAt
) {}
