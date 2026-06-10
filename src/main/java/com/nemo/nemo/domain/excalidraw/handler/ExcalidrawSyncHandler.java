package com.nemo.nemo.domain.excalidraw.handler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.album.service.MemberRoleCacheService;
import com.nemo.nemo.domain.auth.service.JwtTokenService;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.excalidraw.entity.ExcalidrawPage;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.excalidraw.service.ElementDiffApplier;
import com.nemo.nemo.domain.excalidraw.service.PageDocumentStore;
import com.nemo.nemo.domain.sync.service.ClockManager;
import com.nemo.nemo.domain.sync.service.PresenceManager;
import com.nemo.nemo.domain.sync.service.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final AlbumRepository albumRepository;
    private final MemberRoleCacheService roleCacheService;
    private final JwtTokenService jwtTokenService;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskScheduler taskScheduler;

    // N-CORE-12: WS push rate limiting — sessionId → push count in current minute
    private final ConcurrentHashMap<String, AtomicInteger> pushCounters = new ConcurrentHashMap<>();
    private static final int MAX_PUSH_PER_MINUTE = 120;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> keepAliveTimers = new ConcurrentHashMap<>();

    // sessionId → pageId (해당 세션이 현재 보고 있는 페이지)
    private final ConcurrentHashMap<String, String> sessionCurrentPage = new ConcurrentHashMap<>();

    // pageId 단위 락: load→merge→store 원자성 보장
    private final ConcurrentHashMap<String, Object> pageLocks = new ConcurrentHashMap<>();

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

        // connect 이전 메시지는 userId 미설정 상태이므로 connect만 허용
        if (userId == null && !"connect".equals(type)) {
            sendJson(session, Map.of("type", "error", "error", "auth-required"));
            return;
        }

        switch (type) {
            case "connect"          -> handleConnect(session, albumId, root);
            case "push"             -> handlePush(session, albumId, userId, root);
            case "presence"         -> handlePresence(session, albumId, userId, root);
            case "excalidraw_file"  -> handleExcalidrawFile(session, albumId, userId, root);
            case "ping"             -> sendJson(session, Map.of("type", "pong"));
            default                 -> log.debug("[Excalidraw] unknown type: {}", type);
        }
    }

    /** 접속 시: connect 메시지 본문에서 토큰 검증 후 lastClockByPage 기반 full or delta hydration */
    private void handleConnect(WebSocketSession session, String albumId, JsonNode root) {
        // 토큰 검증 — URL 쿼리 파라미터 대신 메시지 본문에서 추출 (로그/프록시 노출 방지)
        String token = root.path("token").asText(null);
        if (token == null) {
            sendJson(session, Map.of("type", "error", "error", "auth-required"));
            try { session.close(); } catch (Exception ignored) {}
            return;
        }

        String userId;
        try {
            userId = jwtTokenService.extractSubject(token);
        } catch (Exception e) {
            sendJson(session, Map.of("type", "error", "error", "auth-failed"));
            try { session.close(); } catch (Exception ignored) {}
            return;
        }

        if (userId == null) {
            sendJson(session, Map.of("type", "error", "error", "auth-failed"));
            try { session.close(); } catch (Exception ignored) {}
            return;
        }

        // 게스트 토큰: subject = "guest:{albumId}"
        if (userId.startsWith("guest:")) {
            String tokenAlbumId = userId.substring("guest:".length());
            if (!tokenAlbumId.equals(albumId)) {
                sendJson(session, Map.of("type", "error", "error", "auth-failed"));
                try { session.close(); } catch (Exception ignored) {}
                return;
            }
        } else {
            // 일반 사용자: 앨범 멤버십 검증
            boolean isMember = albumMemberRepository
                    .findActiveByAlbumIdAndUserId(UUID.fromString(albumId), UUID.fromString(userId))
                    .isPresent();
            if (!isMember) {
                log.warn("[Excalidraw] connect rejected: user {} not member of album {}", userId, albumId);
                sendJson(session, Map.of("type", "error", "error", "not-member"));
                try { session.close(); } catch (Exception ignored) {}
                return;
            }
        }

        session.getAttributes().put("userId", userId);

        // userName 세션 속성 저장 (presence 표시용)
        String userName;
        if (userId.startsWith("guest:")) {
            userName = "게스트";
        } else {
            userName = memberRepository.findById(UUID.fromString(userId))
                    .map(Member::getNickname)
                    .orElse("Unknown");
        }
        session.getAttributes().put("userName", userName);

        // N-CORE-03: VIEWER에게 isReadonly 전달
        AlbumRole role = roleCacheService.getRole(albumId, userId);
        boolean isReadonly = (role == null || role == AlbumRole.VIEWER);

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

        // 현재 접속 중인 모든 사용자 목록 (자신 포함, 초기 presence 표시용)
        List<Map<String, String>> roomMembers = roomManager.getSessions(albumId).stream()
                .filter(s -> s.getAttributes().get("userName") != null)
                .map(s -> Map.of(
                        "userId", (String) s.getAttributes().getOrDefault("userId", ""),
                        "userName", (String) s.getAttributes().get("userName")
                ))
                .toList();

        if (isDelta) {
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
                    "isReadonly", isReadonly,
                    "deltaByPage", deltaByPage,
                    "roomMembers", roomMembers
            ));
        } else {
            String currentPageIdStr = root.path("currentPageId").asText(null);
            // currentPageId가 null/빈값/"null"이면 첫 번째 페이지로 fallback
            // (WS 연결이 pages API 응답보다 빠른 race condition 대응)
            if ((currentPageIdStr == null || currentPageIdStr.isEmpty() || "null".equals(currentPageIdStr)) && !pages.isEmpty()) {
                currentPageIdStr = pages.get(0).getPageId().toString();
            }
            List<Map<String, Object>> pageList = new ArrayList<>();
            for (ExcalidrawPage page : pages) {
                String pageId = page.getPageId().toString();
                long serverClock = clockManager.get(pageId);
                if (pageId.equals(currentPageIdStr)) {
                    String elements = pageDocumentStore.loadElements(pageId);
                    pageList.add(Map.of(
                            "pageId", pageId,
                            "name", page.getName(),
                            "pageOrder", page.getPageOrder(),
                            "elements", parseElements(elements),
                            "serverClock", serverClock
                    ));
                } else {
                    pageList.add(Map.of(
                            "pageId", pageId,
                            "name", page.getName(),
                            "pageOrder", page.getPageOrder(),
                            "elements", List.of(),
                            "serverClock", serverClock
                    ));
                }
            }
            sendJson(session, Map.of(
                    "type", "connected",
                    "hydrationType", "full",
                    "isReadonly", isReadonly,
                    "pages", pageList,
                    "roomMembers", roomMembers
            ));
        }

        // user_joined 브로드캐스트
        broadcast(albumId, session, Map.of(
                "type", "user_joined",
                "userId", userId,
                "userName", userName
        ));

        log.debug("[Excalidraw] connect handled: albumId={}, pages={}, delta={}", albumId, pages.size(), isDelta);
    }

    /** push: 잠금/VIEWER/rate-limit 차단, LWW merge, diff broadcast */
    private void handlePush(WebSocketSession session, String albumId, String userId, JsonNode root) {
        // B-SEC-02: 앨범 잠금 상태 체크
        boolean locked = albumRepository.findById(UUID.fromString(albumId))
                .map(a -> a.isLocked())
                .orElse(true);
        if (locked) {
            sendJson(session, Map.of("type", "error", "error", "album-locked"));
            return;
        }

        // B-SEC-01 / N-CORE-05: VIEWER 차단 (Redis 역할 캐시 활용)
        AlbumRole role = roleCacheService.getRole(albumId, userId);
        if (role == null || role == AlbumRole.VIEWER) {
            sendJson(session, Map.of("type", "error", "error", "read-only"));
            return;
        }

        // N-CORE-12: WS push rate limiting (120 push/min per session)
        int count = pushCounters
                .computeIfAbsent(session.getId(), k -> new AtomicInteger(0))
                .incrementAndGet();
        if (count > MAX_PUSH_PER_MINUTE) {
            sendJson(session, Map.of("type", "error", "error", "rate-limit-exceeded"));
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

        // N-CORE-11: 메시지 크기 사전 검증 (10MB)
        if (incomingJson.length() > 10 * 1024 * 1024) {
            sendJson(session, Map.of("type", "error", "error", "payload-too-large"));
            return;
        }

        // LWW merge — pageId 단위 락으로 load→merge→store 원자성 보장
        final String[] serverElementsRef = new String[1];
        final String[] mergedRef = new String[1];
        final long[] newClockRef = new long[1];
        final boolean[] rebaseRef = new boolean[1];
        synchronized (pageLocks.computeIfAbsent(pageId, k -> new Object())) {
            serverElementsRef[0] = pageDocumentStore.loadElements(pageId);
            ElementDiffApplier.MergeResult mergeResult = elementDiffApplier.merge(serverElementsRef[0], incomingJson);
            String mergedElements = mergeResult.elements();

            // N-CORE-11: merge 후 non-deleted shape 수 검증 (500개)
            try {
                ElementDiffApplier.ElementCountResult countResult = elementDiffApplier.countNonDeleted(mergedElements);
                if (countResult.count() > 500) {
                    sendJson(session, Map.of("type", "error", "error", "shape-limit-exceeded"));
                    return;
                }
            } catch (Exception ignored) {}

            newClockRef[0] = clockManager.increment(pageId);
            pageDocumentStore.applyAndStore(pageId, mergedElements, newClockRef[0]);
            mergedRef[0] = mergedElements;
            rebaseRef[0] = mergeResult.rebased(); // 서버가 클라이언트 변경을 무시한 경우에만 true
        }

        long newClock = newClockRef[0];

        // push_result to sender
        sendJson(session, Map.of(
                "type", "push_result",
                "clientClock", clientClock,
                "serverClock", newClock,
                "action", rebaseRef[0] ? "rebase" : "commit"
        ));

        // diff broadcast: 변경된 element만 전송해 대역폭 절감
        List<JsonNode> diffNodes = elementDiffApplier.getDiffElements(serverElementsRef[0], mergedRef[0]);
        List<String> deletedIds = diffNodes.stream()
                .filter(el -> el.path("isDeleted").booleanValue(false))
                .map(el -> el.path("id").asText())
                .toList();

        Map<String, Object> patchMsg = new LinkedHashMap<>();
        patchMsg.put("type", "patch");
        patchMsg.put("serverClock", newClock);
        patchMsg.put("pageId", pageId);
        patchMsg.put("elements", diffNodes);
        patchMsg.put("deletedIds", deletedIds);
        broadcast(albumId, session, patchMsg);

        sessionCurrentPage.put(session.getId(), pageId);
        log.debug("[Excalidraw] push handled: pageId={}, newClock={}, diffSize={}", pageId, newClock, diffNodes.size());
    }

    /** excalidraw_file: 이미지 파일 URL을 다른 세션에 브로드캐스트 */
    private void handleExcalidrawFile(WebSocketSession session, String albumId, String userId, JsonNode root) {
        AlbumRole role = roleCacheService.getRole(albumId, userId);
        if (role == null || role == AlbumRole.VIEWER) return;

        String fileId = root.path("fileId").asText(null);
        String url = root.path("url").asText(null);
        if (fileId == null || url == null) return;

        broadcast(albumId, session, Map.of("type", "excalidraw_file", "fileId", fileId, "url", url));
    }

    /** presence: cursor + selectedIds 브로드캐스트 */
    private void handlePresence(WebSocketSession session, String albumId, String userId, JsonNode root) {
        Map<String, Object> presenceData = new LinkedHashMap<>();
        presenceData.put("userId", userId);
        String presenceUserName = (String) session.getAttributes().get("userName");
        presenceData.put("userName", presenceUserName != null ? presenceUserName : "");
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

        pushCounters.remove(session.getId());
        sessionCurrentPage.remove(session.getId());

        String userId = (String) session.getAttributes().get("userId");
        if (userId != null && albumId != null) {
            broadcast(albumId, session, Map.of("type", "user_left", "userId", userId));
        }
        roomManager.leave(albumId, session);

        if (roomManager.isEmpty(albumId)) {
            // 마지막 세션 퇴장: 모든 페이지 즉시 flush
            pageRepository.findByAlbumIdOrderByPageOrder(UUID.fromString(albumId))
                    .forEach(p -> pageDocumentStore.flushToDbNow(p.getPageId().toString()));
            log.info("[Excalidraw] room empty, flushed all pages: albumId={}", albumId);
        }

        log.info("[Excalidraw] disconnected: sessionId={}, albumId={}, status={}", session.getId(), albumId, status);
    }

    /** N-CORE-12: 매 분마다 push 카운터 초기화 */
    @Scheduled(fixedRate = 60_000)
    public void resetPushCounters() {
        pushCounters.values().forEach(c -> c.set(0));
    }

    private void startKeepAlive(WebSocketSession session) {
        ScheduledFuture<?> existing = keepAliveTimers.put(session.getId(),
                taskScheduler.scheduleAtFixedRate(
                        () -> {
                            if (!session.isOpen()) {
                                ScheduledFuture<?> f = keepAliveTimers.remove(session.getId());
                                if (f != null) f.cancel(false);
                                return;
                            }
                            sendJson(session, Map.of("type", "pong"));
                        },
                        Instant.now().plusSeconds(30),
                        Duration.ofSeconds(30)
                )
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
