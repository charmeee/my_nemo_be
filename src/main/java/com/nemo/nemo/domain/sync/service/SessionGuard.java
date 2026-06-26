package com.nemo.nemo.domain.sync.service;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

/**
 * WebSocket 세션 강제 종료기.
 * - forceClose: 특정 유저의 세션만 끊기(권한 박탈, 멤버 추방 시)
 * - forceCloseAll: 룸 전체 끊기(앨범 삭제, 잠금 시)
 * - 종료 전에 force-close 메시지로 사유 전달 후 POLICY_VIOLATION으로 close
 */
@Component
@RequiredArgsConstructor
public class SessionGuard {

    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;

    // 특정 사용자의 WS 세션을 강제 종료
    public void forceClose(String albumId, String userId, String reason) {
        Set<WebSocketSession> sessions = roomManager.getSessions(albumId);
        for (WebSocketSession session : sessions) {
            String sessionUserId = (String) session.getAttributes().get("userId");
            if (userId.equals(sessionUserId)) {
                sendForceClose(session, reason);
            }
        }
    }

    // 앨범의 모든 WS 세션을 강제 종료
    public void forceCloseAll(String albumId, String reason) {
        Set<WebSocketSession> sessions = roomManager.getSessions(albumId);
        for (WebSocketSession session : sessions) {
            sendForceClose(session, reason);
        }
    }

    private void sendForceClose(WebSocketSession session, String reason) {
        try {
            Map<String, Object> msg = Map.of("type", "force-close", "reason", reason);
            // session은 RoomManager의 decorated 세션이라 송신이 자동 직렬화된다.
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {}
    }
}
