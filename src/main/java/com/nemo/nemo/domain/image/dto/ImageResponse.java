package com.nemo.nemo.domain.image.dto;

public record ImageResponse(String id, String url, String mimeType, long size, String createdAt) {}
