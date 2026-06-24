package com.nemo.nemo.domain.notification.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.config.AppProperties;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.notification.dto.NotificationResponse;
import com.nemo.nemo.domain.notification.entity.Notification;
import com.nemo.nemo.domain.notification.entity.NotificationType;
import com.nemo.nemo.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer redisListenerContainer;
    private final AppProperties appProperties;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MessageListener> redisListeners = new ConcurrentHashMap<>();

    // SSE Emitter 생성 + 사용자별 Redis Pub/Sub 채널 구독 등록
    // @Transactional 을 붙이면 SSE 응답이 30분 살아있는 동안 OSIV(open-in-view=true)
    // 가 connection 을 풀에 반환하지 않아 Hikari pool 이 고갈된다. 트랜잭션 없이 처리.
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // N-NOTIF-07: Redis Pub/Sub 구독 — 다른 인스턴스에서 publish된 알림도 수신
        MessageListener listener = (message, pattern) -> {
            SseEmitter e = emitters.get(userId);
            if (e == null) return;
            try {
                String json = new String(message.getBody(), StandardCharsets.UTF_8);
                e.send(SseEmitter.event().name("notification").data(json));
            } catch (IOException ex) {
                cleanupEmitter(userId);
            }
        };
        ChannelTopic topic = new ChannelTopic(appProperties.getRedis().getKeyPrefix() + "notification:" + userId);
        redisListenerContainer.addMessageListener(listener, topic);
        redisListeners.put(userId, listener);

        Runnable cleanup = () -> cleanupEmitter(userId);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanupEmitter(userId));
        emitter.onCompletion(cleanup);

        emitters.put(userId, emitter);

        try {
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            cleanupEmitter(userId);
        }

        return emitter;
    }

    private void cleanupEmitter(String userId) {
        emitters.remove(userId);
        MessageListener listener = redisListeners.remove(userId);
        if (listener != null) {
            try {
                redisListenerContainer.removeMessageListener(listener);
            } catch (Exception ignored) {}
        }
    }

    // 알림 저장 + Redis Pub/Sub publish (중복 억제 5분, 실패 시 직접 SSE fallback)
    @Transactional
    public void send(String userId, NotificationType type, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
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

        // N-NOTIF-07: Redis Pub/Sub으로 publish → 구독 중인 인스턴스의 SSE로 전달
        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(toResponse(notification));
        } catch (JacksonException e) {
            responseJson = payloadJson;
        }
        String channel = appProperties.getRedis().getKeyPrefix() + "notification:" + userId;
        try {
            stringRedisTemplate.convertAndSend(channel, responseJson);
        } catch (Exception e) {
            // Redis publish 실패 시 직접 SSE 전송으로 fallback
            log.warn("Redis pub/sub publish failed for userId={}, falling back to direct SSE: {}", userId, e.getMessage());
            SseEmitter emitter = emitters.get(userId);
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().name("notification").data(responseJson));
                } catch (IOException ioEx) {
                    cleanupEmitter(userId);
                }
            }
        }
    }

    // 최근 90일 사용자 알림 목록 반환
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String userId) {
        return notificationRepository.findRecentByUserId(
                        UUID.fromString(userId),
                        java.time.LocalDateTime.now().minusDays(90))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(UUID.fromString(userId));
    }

    // 본인 알림인지 검증 후 읽음 처리
    @Transactional
    public void markRead(UUID notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NemoException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getId().equals(UUID.fromString(userId))) {
            throw new NemoException(ErrorCode.ALBUM_ACCESS_DENIED);
        }

        notification.markRead();
    }

    // 사용자의 모든 미읽음 알림 일괄 읽음 처리
    @Transactional
    public void markAllRead(String userId) {
        notificationRepository.markAllRead(UUID.fromString(userId));
    }

    private NotificationResponse toResponse(Notification notification) {
        Object parsedPayload;
        try {
            parsedPayload = objectMapper.readValue(notification.getPayload(), Object.class);
        } catch (JacksonException e) {
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
