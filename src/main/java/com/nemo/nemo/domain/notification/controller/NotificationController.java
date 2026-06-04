package com.nemo.nemo.domain.notification.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.notification.dto.NotificationResponse;
import com.nemo.nemo.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal String userId) {
        return notificationService.subscribe(userId);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getNotifications(userId)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {
        notificationService.markRead(id, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getUnreadCount(userId)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal String userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
