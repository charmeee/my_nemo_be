package com.nemo.nemo.domain.sync.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomManager {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    // 앨범 룸에 WS 세션 등록
    public void join(String albumId, WebSocketSession session) {
        rooms.computeIfAbsent(albumId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    // 앨범 룸에서 WS 세션 제거, 비면 룸 삭제
    public void leave(String albumId, WebSocketSession session) {
        Set<WebSocketSession> sessions = rooms.get(albumId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) rooms.remove(albumId);
        }
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
}
