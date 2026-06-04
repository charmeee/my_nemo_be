package com.nemo.nemo.domain.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SessionGuard {

    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;

    public void forceClose(String albumId, String userId, String reason) {
        Set<WebSocketSession> sessions = roomManager.getSessions(albumId);
        for (WebSocketSession session : sessions) {
            String sessionUserId = (String) session.getAttributes().get("userId");
            if (userId.equals(sessionUserId)) {
                sendForceClose(session, reason);
            }
        }
    }

    public void forceCloseAll(String albumId, String reason) {
        Set<WebSocketSession> sessions = roomManager.getSessions(albumId);
        for (WebSocketSession session : sessions) {
            sendForceClose(session, reason);
        }
    }

    private void sendForceClose(WebSocketSession session, String reason) {
        try {
            Map<String, Object> msg = Map.of("type", "force-close", "reason", reason);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (Exception ignored) {}
    }
}
