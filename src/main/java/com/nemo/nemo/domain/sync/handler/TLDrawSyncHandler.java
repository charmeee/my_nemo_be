package com.nemo.nemo.domain.sync.handler;

import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.domain.sync.dto.ClientMessage;
import com.nemo.nemo.domain.sync.dto.ServerMessage;
import com.nemo.nemo.domain.sync.service.ClockManager;
import com.nemo.nemo.domain.sync.service.DocumentStore;
import com.nemo.nemo.domain.sync.service.PresenceManager;
import com.nemo.nemo.domain.sync.service.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TLDrawSyncHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;
    private final DocumentStore documentStore;
    private final ClockManager clockManager;
    private final PresenceManager presenceManager;
    private final ObjectMapper objectMapper;

    // albumId → 문서 상태 Map (같은 앨범의 세션들이 공유)
    private final ConcurrentHashMap<String, Map<String, Object>> albumStates = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pingTimers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String albumId = (String) session.getAttributes().get("albumId");
        String userId = (String) session.getAttributes().get("userId");
        roomManager.join(albumId, session);
        log.info("WebSocket connected: sessionId={}, albumId={}, userId={}", session.getId(), albumId, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String albumId = (String) session.getAttributes().get("albumId");
        String userId = (String) session.getAttributes().get("userId");

        ClientMessage msg;
        try {
            msg = objectMapper.readValue(message.getPayload(), ClientMessage.class);
        } catch (Exception e) {
            sendMessage(session, ServerMessage.error("invalid message format"));
            return;
        }

        if (msg.getType() == null) {
            sendMessage(session, ServerMessage.error("missing type field"));
            return;
        }

        switch (msg.getType()) {
            case "connect" -> handleConnect(session, albumId, userId, msg);
            case "push" -> handlePush(session, albumId, msg);
            case "ping" -> handlePing(session);
            default -> sendMessage(session, ServerMessage.error("unknown message type: " + msg.getType()));
        }
    }

    private void handleConnect(WebSocketSession session, String albumId, String userId, ClientMessage msg) {
        Map<String, Object> state = albumStates.computeIfAbsent(albumId, documentStore::load);
        long serverClock = clockManager.get(albumId);
        sendMessage(session, ServerMessage.connect(serverClock, "entire_document", state));
        resetPingTimer(session);
        log.debug("connect handled: sessionId={}, albumId={}, serverClock={}", session.getId(), albumId, serverClock);
    }

    private void handlePush(WebSocketSession session, String albumId, ClientMessage msg) {
        Map<String, Object> state = albumStates.computeIfAbsent(albumId, documentStore::load);

        if (msg.getDiff() != null) {
            documentStore.applyDiff(albumId, state, msg.getDiff());
            long newClock = clockManager.increment(albumId);
            broadcast(albumId, session, ServerMessage.patch(newClock, msg.getDiff()));
        }

        if (msg.getPresence() != null) {
            Set<WebSocketSession> sessions = roomManager.getSessions(albumId);
            presenceManager.broadcast(albumId, session.getId(), msg.getPresence(), sessions);
        }

        resetPingTimer(session);
    }

    private void handlePing(WebSocketSession session) {
        sendMessage(session, ServerMessage.pong());
        resetPingTimer(session);
    }

    private void resetPingTimer(WebSocketSession session) {
        ScheduledFuture<?> existing = pingTimers.remove(session.getId());
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (session.isOpen()) {
                try {
                    log.warn("Ping timeout, closing session: {}", session.getId());
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (Exception ignored) {}
            }
        }, 30, TimeUnit.SECONDS);

        pingTimers.put(session.getId(), future);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String albumId = (String) session.getAttributes().get("albumId");

        ScheduledFuture<?> timer = pingTimers.remove(session.getId());
        if (timer != null) {
            timer.cancel(false);
        }

        roomManager.leave(albumId, session);

        if (roomManager.isEmpty(albumId)) {
            documentStore.flushToDb(albumId);
            albumStates.remove(albumId);
            clockManager.remove(albumId);
            log.info("Room empty, flushed to DB: albumId={}", albumId);
        }

        log.info("WebSocket disconnected: sessionId={}, albumId={}, status={}", session.getId(), albumId, status);
    }

    private void sendMessage(WebSocketSession session, ServerMessage msg) {
        try {
            String payload = objectMapper.writeValueAsString(msg);
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.warn("Failed to send message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void broadcast(String albumId, WebSocketSession senderSession, ServerMessage msg) {
        Set<WebSocketSession> sessions = roomManager.getSessions(albumId);
        for (WebSocketSession session : sessions) {
            if (session.isOpen() && !session.getId().equals(senderSession.getId())) {
                sendMessage(session, msg);
            }
        }
    }
}
