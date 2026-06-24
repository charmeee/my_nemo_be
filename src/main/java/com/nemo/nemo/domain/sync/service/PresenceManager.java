package com.nemo.nemo.domain.sync.service;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PresenceManager {

    private final ObjectMapper objectMapper;

    // presence 메시지를 발신자 제외 룸 멤버들에게 브로드캐스트
    public void broadcast(String albumId, String senderSessionId,
                          Map<String, Object> presence, Set<WebSocketSession> sessions) {
        if (presence == null || sessions.isEmpty()) return;

        Map<String, Object> presenceMsg = new HashMap<>();
        presenceMsg.put("type", "presence");
        presenceMsg.put("sessionId", senderSessionId);
        presenceMsg.put("presence", presence);

        String msg;
        try {
            msg = objectMapper.writeValueAsString(presenceMsg);
        } catch (Exception e) {
            return;
        }

        TextMessage textMsg = new TextMessage(msg);
        for (WebSocketSession session : sessions) {
            if (session.isOpen() && !session.getId().equals(senderSessionId)) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMsg);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
