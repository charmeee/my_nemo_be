package com.nemo.nemo.domain.excalidraw.handler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.excalidraw.entity.ExcalidrawPage;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.excalidraw.service.ElementDiffApplier;
import com.nemo.nemo.domain.excalidraw.service.PageDocumentStore;
import com.nemo.nemo.domain.sync.service.ClockManager;
import com.nemo.nemo.domain.sync.service.PresenceManager;
import com.nemo.nemo.domain.sync.service.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Excalidraw 기반 실시간 협업 WebSocket 핸들러.
 * URI: /sync/excalidraw/{albumId}
 *
 * 프로토콜:
 *  Client → Server: connect | push | presence | ping
 *  Server → Client: connected | patch | push_result | pong | page_event | error | force-close
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcalidrawSyncHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;
    private final PageDocumentStore pageDocumentStore;
    private final ClockManager clockManager;
    private final PresenceManager presenceManager;
    private final ElementDiffApplier elementDiffApplier;
    private final ExcalidrawPageRepository pageRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> keepAliveTimers = new ConcurrentHashMap<>();

    // sessionId → Set<pageId> (해당 세션이 현재 로드한 페이지들)
    private final ConcurrentHashMap<String, String> sessionCurrentPage = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String albumId = (String) session.getAttributes().get("albumId");
        String userId = (String) session.getAttributes().get("userId");
        roomManager.join(albumId, session);
        startKeepAlive(session);
        log.info("[Excalidraw] connected: sessionId={}, albumId={}, userId={}", session.getId(), albumId, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String albumId = (String) session.getAttributes().get("albumId");
        String userId = (String) session.getAttributes().get("userId");
        String payload = message.getPayload();

        if (!payload.startsWith("{")) return; // chunked or non-JSON

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.debug("[Excalidraw] parse error: {}", e.getMessage());
            return;
        }

        String type = root.path("type").asText(null);
        if (type == null) return;

        switch (type) {
            case "connect"  -> handleConnect(session, albumId, root);
            case "push"     -> handlePush(session, albumId, userId, root);
            case "presence" -> handlePresence(session, albumId, userId, root);
            case "ping"     -> sendJson(session, Map.of("type", "pong"));
            default         -> log.debug("[Excalidraw] unknown type: {}", type);
        }
    }

    /** 접속 시: lastClockByPage 기반 full or delta hydration */
    private void handleConnect(WebSocketSession session, String albumId, JsonNode root) {
        Map<String, Long> lastClockByPage = new LinkedHashMap<>();
        JsonNode clocks = root.path("lastClockByPage");
        if (clocks.isObject()) {
            for (Map.Entry<String, JsonNode> entry : clocks.properties()) {
                lastClockByPage.put(entry.getKey(), entry.getValue().longValue(0));
            }
        }

        List<ExcalidrawPage> pages = pageRepository.findByAlbumIdOrderByPageOrder(UUID.fromString(albumId));

        boolean isDelta = !lastClockByPage.isEmpty() &&
                pages.stream().allMatch(p -> lastClockByPage.containsKey(p.getPageId().toString()));

        if (isDelta) {
            // delta: 클라이언트가 lastClock 이후 변경된 것만 전달
            // 현재 구현: serverClock이 lastClock보다 크면 전체 page elements 전달
            Map<String, Object> deltaByPage = new LinkedHashMap<>();
            for (ExcalidrawPage page : pages) {
                String pageId = page.getPageId().toString();
                long clientClock = lastClockByPage.getOrDefault(pageId, 0L);
                long serverClock = clockManager.get(pageId);
                if (serverClock > clientClock) {
                    String elements = pageDocumentStore.loadElements(pageId);
                    deltaByPage.put(pageId, Map.of(
                            "elements", parseElements(elements),
                            "serverClock", serverClock
                    ));
                }
            }
            sendJson(session, Map.of(
                    "type", "connected",
                    "hydrationType", "delta",
                    "deltaByPage", deltaByPage
            ));
        } else {
            // full: 모든 페이지 전체 state 전달
            List<Map<String, Object>> pageList = new ArrayList<>();
            for (ExcalidrawPage page : pages) {
                String pageId = page.getPageId().toString();
                String elements = pageDocumentStore.loadElements(pageId);
                long serverClock = clockManager.get(pageId);
                pageList.add(Map.of(
                        "pageId", pageId,
                        "name", page.getName(),
                        "pageOrder", page.getPageOrder(),
                        "elements", parseElements(elements),
                        "serverClock", serverClock
                ));
            }
            sendJson(session, Map.of(
                    "type", "connected",
                    "hydrationType", "full",
                    "pages", pageList
            ));
        }

        log.debug("[Excalidraw] connect handled: albumId={}, pages={}, delta={}", albumId, pages.size(), isDelta);
    }

    /** push: VIEWER 차단, LWW merge, broadcast patch */
    private void handlePush(WebSocketSession session, String albumId, String userId, JsonNode root) {
        boolean isViewer = albumMemberRepository
                .findActiveByAlbumIdAndUserId(UUID.fromString(albumId), UUID.fromString(userId))
                .map(am -> am.getRole() == AlbumRole.VIEWER)
                .orElse(true);
        if (isViewer) {
            sendJson(session, Map.of("type", "error", "error", "read-only"));
            return;
        }

        String pageId = root.path("pageId").asText(null);
        long clientClock = root.path("clientClock").longValue(0);
        JsonNode elementsNode = root.path("elements");

        if (pageId == null || !elementsNode.isArray()) return;

        String incomingJson;
        try {
            incomingJson = objectMapper.writeValueAsString(elementsNode);
        } catch (Exception e) {
            return;
        }

        // LWW merge
        String serverElements = pageDocumentStore.loadElements(pageId);
        String mergedElements = elementDiffApplier.merge(serverElements, incomingJson);
        long newClock = clockManager.increment(pageId);

        pageDocumentStore.applyAndStore(pageId, mergedElements, newClock);

        // push_result to sender
        boolean rebase = !mergedElements.equals(serverElements);
        sendJson(session, Map.of(
                "type", "push_result",
                "clientClock", clientClock,
                "serverClock", newClock,
                "action", rebase ? "rebase" : "commit"
        ));

        // patch broadcast to other sessions
        Object parsedElements = parseElements(mergedElements);
        Map<String, Object> patchMsg = Map.of(
                "type", "patch",
                "serverClock", newClock,
                "pageId", pageId,
                "elements", parsedElements
        );
        broadcast(albumId, session, patchMsg);

        sessionCurrentPage.put(session.getId(), pageId);
        log.debug("[Excalidraw] push handled: pageId={}, newClock={}", pageId, newClock);
    }

    /** presence: cursor + selectedIds 브로드캐스트 */
    private void handlePresence(WebSocketSession session, String albumId, String userId, JsonNode root) {
        Map<String, Object> presenceData = new LinkedHashMap<>();
        presenceData.put("userId", userId);
        presenceData.put("pageId", root.path("pageId").asText(""));
        presenceData.put("cursor", parseNode(root.path("cursor")));
        presenceData.put("selectedIds", parseNode(root.path("selectedIds")));

        Set<WebSocketSession> sessions = roomManager.getSessions(albumId);
        presenceManager.broadcast(albumId, session.getId(), presenceData, sessions);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String albumId = (String) session.getAttributes().get("albumId");

        ScheduledFuture<?> timer = keepAliveTimers.remove(session.getId());
        if (timer != null) timer.cancel(false);

        String pageId = sessionCurrentPage.remove(session.getId());
        roomManager.leave(albumId, session);

        if (roomManager.isEmpty(albumId)) {
            // 마지막 세션 퇴장: 모든 페이지 즉시 flush
            pageRepository.findByAlbumIdOrderByPageOrder(UUID.fromString(albumId))
                    .forEach(p -> pageDocumentStore.flushToDbNow(p.getPageId().toString()));
            log.info("[Excalidraw] room empty, flushed all pages: albumId={}", albumId);
        }

        log.info("[Excalidraw] disconnected: sessionId={}, albumId={}, status={}", session.getId(), albumId, status);
    }

    private void startKeepAlive(WebSocketSession session) {
        ScheduledFuture<?> existing = keepAliveTimers.put(session.getId(),
                scheduler.scheduleAtFixedRate(() -> {
                    if (!session.isOpen()) {
                        ScheduledFuture<?> f = keepAliveTimers.remove(session.getId());
                        if (f != null) f.cancel(false);
                        return;
                    }
                    sendJson(session, Map.of("type", "pong"));
                }, 30, 30, TimeUnit.SECONDS)
        );
        if (existing != null) existing.cancel(false);
    }

    private void sendJson(WebSocketSession session, Object obj) {
        try {
            String payload = objectMapper.writeValueAsString(obj);
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.warn("[Excalidraw] send failed: {}", e.getMessage());
        }
    }

    private void broadcast(String albumId, WebSocketSession sender, Object msg) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            return;
        }
        roomManager.getSessions(albumId).forEach(s -> {
            if (s.isOpen() && !s.getId().equals(sender.getId())) {
                try {
                    synchronized (s) { s.sendMessage(new TextMessage(payload)); }
                } catch (Exception ignored) {}
            }
        });
    }

    private Object parseElements(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Object parseNode(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
