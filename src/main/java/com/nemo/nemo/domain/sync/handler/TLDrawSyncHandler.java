package com.nemo.nemo.domain.sync.handler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final AlbumMemberRepository albumMemberRepository;

    // albumId → 문서 상태 Map (같은 앨범의 세션들이 공유)
    private final ConcurrentHashMap<String, Map<String, Object>> albumStates = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pingTimers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String albumId = (String) session.getAttributes().get("albumId");
        String userId = (String) session.getAttributes().get("userId");
        roomManager.join(albumId, session);
        startKeepAlive(session);
        log.info("WebSocket connected: sessionId={}, albumId={}, userId={}", session.getId(), albumId, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String albumId = (String) session.getAttributes().get("albumId");
        String userId = (String) session.getAttributes().get("userId");
        String payload = message.getPayload();

        // TLDraw may chunk large messages (prefix: "N_data"). Skip partial chunks silently.
        if (!payload.startsWith("{")) {
            log.debug("Skipping non-JSON frame: {}", payload.substring(0, Math.min(40, payload.length())));
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.debug("Failed to parse message: {}", e.getMessage());
            return;
        }

        String type = root.path("type").asText(null);
        if (type == null) return;

        log.debug("handleTextMessage: sessionId={}, albumId={}, type={}", session.getId(), albumId, type);
        switch (type) {
            case "connect" -> handleConnect(session, albumId, userId, root);
            case "push" -> handlePush(session, albumId, root);
            case "ping" -> sendMessage(session, ServerMessage.pong());
            default -> log.debug("Unknown message type: {}", type);
        }
    }

    private void handleConnect(WebSocketSession session, String albumId, String userId, JsonNode root) {
        Map<String, Object> state = albumStates.computeIfAbsent(albumId, documentStore::load);
        long serverClock = clockManager.get(albumId);

        // TLDraw NetworkDiff format: {recordId → ["put", record]}
        Map<String, Object> connectDiff = new HashMap<>(state.size());
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            connectDiff.put(entry.getKey(), List.of("put", entry.getValue()));
        }

        String connectRequestId = root.has("connectRequestId") ? root.get("connectRequestId").asText() : null;
        Map<String, Object> schema = root.has("schema") && root.get("schema").isObject()
                ? nodeToMap(root.get("schema")) : null;

        sendMessage(session, ServerMessage.connect(serverClock, connectDiff, connectRequestId, schema));
        log.debug("connect handled: sessionId={}, albumId={}, serverClock={}, shapes={}", session.getId(), albumId, serverClock, state.size());
    }

    private void handlePush(WebSocketSession session, String albumId, JsonNode root) {
        String userId = (String) session.getAttributes().get("userId");
        boolean isViewer = albumMemberRepository
                .findActiveByAlbumIdAndUserId(UUID.fromString(albumId), UUID.fromString(userId))
                .map(am -> am.getRole() == AlbumRole.VIEWER)
                .orElse(true);
        if (isViewer) {
            log.debug("[handlePush] VIEWER {} rejected for albumId={}", userId, albumId);
            return;
        }

        Map<String, Object> state = albumStates.computeIfAbsent(albumId, documentStore::load);
        long newClock = clockManager.get(albumId);

        JsonNode diffNode = root.get("diff");
        if (diffNode != null && diffNode.isObject() && !diffNode.isEmpty()) {
            Map<String, Object> diff = diffNodeToMap(diffNode);
            documentStore.applyDiff(albumId, state, diff);
            newClock = clockManager.increment(albumId);
            broadcast(albumId, session, ServerMessage.patch(newClock, diff));
            log.debug("[handlePush] applied diff, newClock={}, stateSize={}", newClock, state.size());
        }

        long clientClock = root.has("clientClock") ? root.get("clientClock").longValue() : 0L;
        sendMessage(session, ServerMessage.pushResult(clientClock, newClock));

        JsonNode presenceNode = root.get("presence");
        if (presenceNode != null && presenceNode.isObject() && !presenceNode.isEmpty()) {
            Set<WebSocketSession> sessions = roomManager.getSessions(albumId);
            presenceManager.broadcast(albumId, session.getId(), nodeToMap(presenceNode), sessions);
        }
    }

    /** Convert a JsonNode representing a TLDraw NetworkDiff object to Map<String, Object>.
     *  Each entry value is an array [opType, record?] → List<Object>. */
    private Map<String, Object> diffNodeToMap(JsonNode diffNode) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : diffNode.properties()) {
            JsonNode val = entry.getValue();
            result.put(entry.getKey(), val.isArray() ? nodeToList(val) : nodeToValue(val));
        }
        return result;
    }

    private List<Object> nodeToList(JsonNode arrayNode) {
        List<Object> list = new ArrayList<>(arrayNode.size());
        for (JsonNode item : arrayNode) {
            list.add(nodeToValue(item));
        }
        return list;
    }

    private Map<String, Object> nodeToMap(JsonNode objectNode) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
            map.put(entry.getKey(), nodeToValue(entry.getValue()));
        }
        return map;
    }

    private Object nodeToValue(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isNull()) return null;
        if (node.isArray()) return nodeToList(node);
        if (node.isObject()) return nodeToMap(node);
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.doubleValue();
        return node.asText();
    }

    /** Server-initiated keep-alive: send JSON {"type":"pong"} every 30s */
    private void startKeepAlive(WebSocketSession session) {
        ScheduledFuture<?> existing = pingTimers.put(session.getId(),
            scheduler.scheduleAtFixedRate(() -> {
                if (!session.isOpen()) {
                    ScheduledFuture<?> f = pingTimers.remove(session.getId());
                    if (f != null) f.cancel(false);
                    return;
                }
                sendMessage(session, ServerMessage.pong());
            }, 30, 30, TimeUnit.SECONDS)
        );
        if (existing != null) existing.cancel(false);
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
