package com.nemo.nemo.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.notification.dto.NotificationResponse;
import com.nemo.nemo.domain.notification.entity.Notification;
import com.nemo.nemo.domain.notification.entity.NotificationType;
import com.nemo.nemo.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;
    private static final int MAX_NOTIFICATIONS = 100;

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));
        emitter.onCompletion(() -> emitters.remove(userId));

        emitters.put(userId, emitter);

        try {
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            emitters.remove(userId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Transactional
    public void send(String userId, NotificationType type, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new NemoException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        String albumId = extractAlbumId(payload);
        if (albumId != null) {
            String suppressKey = "notification:suppress:" + albumId + ":" + userId + ":" + type.name();
            Boolean alreadySent = stringRedisTemplate.hasKey(suppressKey);
            if (Boolean.TRUE.equals(alreadySent)) {
                return;
            }
            stringRedisTemplate.opsForValue().set(suppressKey, "1", Duration.ofMinutes(5));
        }

        Member member = memberRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_FOUND));

        Notification notification = Notification.create(member, type, payloadJson);
        notificationRepository.save(notification);

        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(toResponse(notification)));
            } catch (IOException e) {
                emitters.remove(userId);
                emitter.completeWithError(e);
            }
        }

        String channel = "notification:" + userId;
        try {
            stringRedisTemplate.convertAndSend(channel, payloadJson);
        } catch (Exception e) {
            log.warn("Redis pub/sub publish failed for userId={}: {}", userId, e.getMessage());
        }
    }

    public List<NotificationResponse> getNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(
                        UUID.fromString(userId),
                        PageRequest.of(0, MAX_NOTIFICATIONS))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void markRead(UUID notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NemoException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getId().equals(UUID.fromString(userId))) {
            throw new NemoException(ErrorCode.ALBUM_ACCESS_DENIED);
        }

        notification.markRead();
    }

    @Transactional
    public void markAllRead(String userId) {
        notificationRepository.markAllRead(UUID.fromString(userId));
    }

    private NotificationResponse toResponse(Notification notification) {
        Object parsedPayload;
        try {
            parsedPayload = objectMapper.readValue(notification.getPayload(), Object.class);
        } catch (JsonProcessingException e) {
            parsedPayload = notification.getPayload();
        }

        return new NotificationResponse(
                notification.getId().toString(),
                notification.getType().name(),
                parsedPayload,
                notification.isRead(),
                notification.getCreatedAt() != null
                        ? notification.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null
        );
    }

    @SuppressWarnings("unchecked")
    private String extractAlbumId(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object albumId = map.get("albumId");
            return albumId != null ? albumId.toString() : null;
        }
        return null;
    }
}
