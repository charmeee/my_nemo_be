package com.nemo.nemo.domain.sync.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 앨범(룸) 단위 WebSocket 세션 보관소.
 * - 모든 세션은 ConcurrentWebSocketSessionDecorator로 한 번 wrap 후 보관·반환된다.
 *   동일 세션에 대한 동시 sendMessage 호출을 내부 큐로 직렬화하므로
 *   호출자가 별도의 synchronized를 걸 필요가 없다.
 * - 한 유저가 여러 탭/디바이스로 접속 시 같은 룸에 세션이 여러 개 존재.
 */
@Component
public class RoomManager {

    // 송신 큐가 비기까지 기다리는 최대 시간(ms). 초과 시 connection close.
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    // 직렬화 후 버퍼 한도(bytes). 초과 시 connection close.
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final ConcurrentHashMap<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentWebSocketSessionDecorator> decorators = new ConcurrentHashMap<>();

    // 앨범 룸에 WS 세션 등록 (decorator로 wrap하여 저장)
    public void join(String albumId, WebSocketSession session) {
        WebSocketSession wrapped = decorate(session);
        rooms.computeIfAbsent(albumId, k -> ConcurrentHashMap.newKeySet()).add(wrapped);
    }

    // 앨범 룸에서 WS 세션 제거, 비면 룸 삭제, decorator 정리
    public void leave(String albumId, WebSocketSession session) {
        Set<WebSocketSession> sessions = rooms.get(albumId);
        if (sessions != null) {
            ConcurrentWebSocketSessionDecorator wrapped = decorators.get(session.getId());
            sessions.remove(wrapped != null ? wrapped : session);
            if (sessions.isEmpty()) rooms.remove(albumId);
        }
        decorators.remove(session.getId());
    }

    public Set<WebSocketSession> getSessions(String albumId) {
        return rooms.getOrDefault(albumId, Set.of());
    }

    public boolean isEmpty(String albumId) {
        Set<WebSocketSession> sessions = rooms.get(albumId);
        return sessions == null || sessions.isEmpty();
    }

    public int size(String albumId) {
        return rooms.getOrDefault(albumId, Set.of()).size();
    }

    /**
     * raw 세션을 decorator로 wrap(없으면 생성)해서 반환.
     * pre-connect 단계에서 sendMessage가 호출되어도 동일 decorator를 재사용한다.
     */
    public WebSocketSession decorate(WebSocketSession session) {
        if (session == null) return null;
        return decorators.computeIfAbsent(
                session.getId(),
                id -> new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES)
        );
    }
}
