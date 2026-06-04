package com.nemo.nemo.domain.notification.dto;

public record NotificationResponse(String id, String type, Object payload, boolean isRead, String createdAt) {}
