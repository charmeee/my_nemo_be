package com.nemo.nemo.ws;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excalidraw WebSocket 통합 테스트.
 * RANDOM_PORT 로 실제 서버를 띄우고 StandardWebSocketClient 로 WS 연결.
 *
 * 커버 시나리오:
 *   TC-WS-01: connect → connected 응답
 *   TC-WS-02: ping → pong
 *   TC-WS-03: EDITOR push → push_result
 *   TC-WS-04: VIEWER push → error:read-only
 *   TC-WS-05: Alice push → Bob 에게 patch broadcast
 *   TC-WS-06: 잠긴 앨범 push → error:album-locked
 *   TC-WS-07: push 121회 → error:rate-limit-exceeded
 *   TC-WS-08: 501개 element push → error:shape-limit-exceeded
 *   TC-WS-09: presence 전송 → 상대방에게 broadcast
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Excalidraw WebSocket Integration Tests")
class ExcalidrawWsTest {

    /** TC-WS-08 대용량 메시지 테스트를 위해 WS 버퍼를 10MB로 확장 */
    @TestConfiguration
    static class WsBufferConfig {
        @Bean
        public ServletServerContainerFactoryBean createWebSocketContainer() {
            ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
            container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
            return container;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    // Java 11+ HttpClient - PATCH 지원
    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    private String aliceToken;
    private String bobToken;
    private String carolToken;
    private String aliceId;

    private String albumId;
    private String pageId;

    private final List<WsConn> openConnections = new ArrayList<>();

    // ────────────────────────────────────────────
    // Setup / Teardown
    // ────────────────────────────────────────────

    @BeforeEach
    void setup() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        aliceToken = testLogin("alice-ws-" + suffix + "@test.com", "Alice");
        bobToken   = testLogin("bob-ws-"   + suffix + "@test.com", "Bob");
        carolToken = testLogin("carol-ws-" + suffix + "@test.com", "Carol");

        aliceId = extractUserId(aliceToken);

        albumId = createAlbum(aliceToken, "[TC-WS] Test Album " + suffix);
        pageId  = getFirstPageId(aliceToken, albumId);

        String editorCode = createInvite(aliceToken, albumId, "EDITOR", false);
        joinInvite(bobToken, editorCode);

        String viewerCode = createInvite(aliceToken, albumId, "VIEWER", false);
        joinInvite(carolToken, viewerCode);
    }

    @AfterEach
    void teardown() {
        for (WsConn c : openConnections) {
            try { c.close(); } catch (Exception ignored) {}
        }
        openConnections.clear();
    }

    // ────────────────────────────────────────────
    // TC-WS-01: connect → connected 응답
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-01: WS connect 메시지 → connected 응답 수신")
    void ws01_connect_receivesConnected() throws Exception {
        WsConn alice = connect(albumId, aliceToken);

        alice.send(connectMsg(aliceToken));
        String msg = alice.poll(5000);

        assertThat(msg).isNotNull();
        JsonNode node = objectMapper.readTree(msg);
        assertThat(node.path("type").asText()).isEqualTo("connected");
        assertThat(node.path("hydrationType").asText()).isIn("full", "delta");
    }

    // ────────────────────────────────────────────
    // TC-WS-02: ping → pong
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-02: ping 전송 → pong 수신")
    void ws02_ping_pong() throws Exception {
        WsConn alice = connect(albumId, aliceToken);
        alice.send(connectMsg(aliceToken));
        alice.poll(5000); // connected

        alice.send("{\"type\":\"ping\"}");
        String msg = alice.poll(5000);

        assertThat(msg).isNotNull();
        assertThat(objectMapper.readTree(msg).path("type").asText()).isEqualTo("pong");
    }

    // ────────────────────────────────────────────
    // TC-WS-03: EDITOR push → push_result
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-03: EDITOR(ADMIN) push → push_result 수신")
    void ws03_editorPush_receivesPushResult() throws Exception {
        WsConn alice = connect(albumId, aliceToken);
        alice.send(connectMsg(aliceToken));
        alice.poll(5000); // connected

        String pushMsg = buildPush(pageId, 0, singleElement("el-1", 1));
        alice.send(pushMsg);

        String result = alice.poll(5000);
        assertThat(result).isNotNull();
        JsonNode node = objectMapper.readTree(result);
        assertThat(node.path("type").asText()).isEqualTo("push_result");
        assertThat(node.path("serverClock").asLong()).isGreaterThan(0);
    }

    // ────────────────────────────────────────────
    // TC-WS-04: VIEWER push → error:read-only
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-04: VIEWER push → error:read-only 차단")
    void ws04_viewerPush_receivesReadOnlyError() throws Exception {
        WsConn carol = connect(albumId, carolToken);
        carol.send(connectMsg(carolToken));
        carol.poll(5000); // connected

        carol.send(buildPush(pageId, 0, "[]"));
        String result = carol.poll(5000);

        assertThat(result).isNotNull();
        JsonNode node = objectMapper.readTree(result);
        assertThat(node.path("type").asText()).isEqualTo("error");
        assertThat(node.path("error").asText()).isEqualTo("read-only");
    }

    // ────────────────────────────────────────────
    // TC-WS-05: Alice push → Bob에게 patch broadcast
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-05: Alice push → Bob에게 patch broadcast")
    void ws05_push_broadcastsToBob() throws Exception {
        WsConn alice = connect(albumId, aliceToken);
        WsConn bob   = connect(albumId, bobToken);

        alice.send(connectMsg(aliceToken));
        alice.poll(5000);
        bob.send(connectMsg(bobToken));
        bob.poll(5000);

        alice.send(buildPush(pageId, 0, singleElement("el-broadcast", 1)));

        alice.poll(5000); // push_result to alice
        String bobMsg = bob.poll(5000);

        assertThat(bobMsg).isNotNull();
        JsonNode node = objectMapper.readTree(bobMsg);
        assertThat(node.path("type").asText()).isEqualTo("patch");
        assertThat(node.path("pageId").asText()).isEqualTo(pageId);
    }

    // ────────────────────────────────────────────
    // TC-WS-06: 앨범 잠금 → push → album-locked 에러
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-06: 잠긴 앨범 push → error:album-locked")
    void ws06_lockedAlbum_receivesAlbumLockedError() throws Exception {
        // 앨범 잠금 (AlbumUpdateRequest 필드명: isLocked)
        patchAlbum(aliceToken, albumId, "{\"isLocked\":true}");

        WsConn alice = connect(albumId, aliceToken);
        alice.send(connectMsg(aliceToken));
        alice.poll(5000);

        alice.send(buildPush(pageId, 0, singleElement("el-locked", 1)));
        String result = alice.poll(5000);

        assertThat(result).isNotNull();
        JsonNode node = objectMapper.readTree(result);
        assertThat(node.path("type").asText()).isEqualTo("error");
        assertThat(node.path("error").asText()).isEqualTo("album-locked");
    }

    // ────────────────────────────────────────────
    // TC-WS-07: 121회 push → rate-limit-exceeded
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-07: 121회 push → rate-limit-exceeded 에러 발생")
    void ws07_rateLimitExceeded() throws Exception {
        WsConn alice = connect(albumId, aliceToken);
        alice.send(connectMsg(aliceToken));
        alice.poll(5000);

        // 121번 push 전송 (120까지는 OK, 121번째부터 rate-limit)
        for (int i = 0; i <= 120; i++) {
            String el = "[{\"id\":\"el-r" + i + "\",\"type\":\"rectangle\","
                    + "\"version\":" + (i + 1) + ",\"versionNonce\":" + i + ","
                    + "\"isDeleted\":false,\"x\":" + i + ",\"y\":0,\"width\":10,\"height\":10}]";
            alice.send(buildPush(pageId, i, el));
        }

        // 응답 수집 — rate-limit-exceeded 가 있어야 함
        boolean rateLimited = false;
        for (int i = 0; i <= 130; i++) {
            String msg = alice.poll(1000);
            if (msg == null) break;
            JsonNode node = objectMapper.readTree(msg);
            if ("error".equals(node.path("type").asText())
                    && "rate-limit-exceeded".equals(node.path("error").asText())) {
                rateLimited = true;
                break;
            }
        }

        assertThat(rateLimited)
                .as("121번째 push에서 rate-limit-exceeded 에러가 발생해야 함")
                .isTrue();
    }

    // ────────────────────────────────────────────
    // TC-WS-08: 501개 element → shape-limit-exceeded
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-08: 501개 element push → shape-limit-exceeded 에러")
    void ws08_shapeLimitExceeded() throws Exception {
        WsConn alice = connect(albumId, aliceToken);
        alice.send(connectMsg(aliceToken));
        alice.poll(5000);

        // 501개 element 빌드
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 501; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"el-s").append(i).append("\",\"type\":\"rectangle\",")
              .append("\"version\":1,\"versionNonce\":").append(i).append(",")
              .append("\"isDeleted\":false,\"x\":").append(i).append(",")
              .append("\"y\":0,\"width\":10,\"height\":10}");
        }
        sb.append("]");

        alice.send(buildPush(pageId, 0, sb.toString()));
        String result = alice.poll(10000);

        assertThat(result).isNotNull();
        JsonNode node = objectMapper.readTree(result);
        assertThat(node.path("type").asText()).isEqualTo("error");
        assertThat(node.path("error").asText()).isEqualTo("shape-limit-exceeded");
    }

    // ────────────────────────────────────────────
    // TC-WS-09: presence 전송 → 상대방 broadcast
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-09: Alice presence 전송 → Bob에게 userId 포함 broadcast")
    void ws09_presence_broadcastsToOther() throws Exception {
        WsConn alice = connect(albumId, aliceToken);
        WsConn bob   = connect(albumId, bobToken);

        alice.send(connectMsg(aliceToken));
        alice.poll(5000);
        bob.send(connectMsg(bobToken));
        bob.poll(5000);

        alice.send("{\"type\":\"presence\",\"pageId\":\"" + pageId + "\","
                + "\"cursor\":{\"x\":100,\"y\":200},\"selectedIds\":[]}");

        String bobMsg = bob.poll(5000);
        assertThat(bobMsg).isNotNull();
        JsonNode node = objectMapper.readTree(bobMsg);
        // presence message: {type:"presence", presence:{userId:...}, sessionId:...}
        assertThat(node.path("type").asText()).isEqualTo("presence");
        assertThat(node.path("presence").path("userId").asText()).isEqualTo(aliceId);
    }

    // ────────────────────────────────────────────
    // TC-WS-10: 초대된 유저 최초 접속 시 currentPageId=null → 기존 elements 수신
    // ────────────────────────────────────────────

    @Test
    @DisplayName("TC-WS-10a: currentPageId 필드 없이 connect → 첫 번째 페이지 elements 정상 수신")
    void ws10a_connect_missingCurrentPageId_receivesElements() throws Exception {
        // Alice: shape 3개 push (서버에 저장)
        WsConn alice = connect(albumId, aliceToken);
        alice.send(connectMsg(aliceToken));
        alice.poll(5000); // connected
        for (int i = 0; i < 3; i++) {
            alice.send(buildPush(pageId, i, singleElement("el-init-" + i, i + 1)));
            alice.poll(3000); // push_result
        }

        // Bob: currentPageId 없이 connect (race condition 재현)
        WsConn bob = connect(albumId, bobToken);
        bob.send(connectMsg(bobToken));

        String msg = bob.poll(5000);
        assertThat(msg).isNotNull();
        JsonNode node = objectMapper.readTree(msg);
        assertThat(node.path("type").asText()).isEqualTo("connected");
        assertThat(node.path("hydrationType").asText()).isEqualTo("full");

        JsonNode pages = node.path("pages");
        assertThat(pages.isArray()).isTrue();
        assertThat(pages.size()).isGreaterThan(0);

        JsonNode firstPage = pages.get(0);
        JsonNode elements = firstPage.path("elements");
        assertThat(elements.isArray()).isTrue();
        assertThat(elements.size())
                .as("currentPageId 없이 connect해도 첫 번째 페이지 elements가 포함되어야 함")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("TC-WS-10b: currentPageId=null (JSON null)로 connect → 첫 번째 페이지 elements 정상 수신")
    void ws10b_connect_nullCurrentPageId_receivesElements() throws Exception {
        // Alice: shape 3개 push
        WsConn alice = connect(albumId, aliceToken);
        alice.send(connectMsg(aliceToken));
        alice.poll(5000); // connected
        for (int i = 0; i < 3; i++) {
            alice.send(buildPush(pageId, i, singleElement("el-null-" + i, i + 1)));
            alice.poll(3000); // push_result
        }

        // Bob: currentPageId=null (JSON null) 로 connect — 프론트엔드 race condition 그대로 재현
        WsConn bob = connect(albumId, bobToken);
        bob.send("{\"type\":\"connect\",\"token\":\"" + bobToken
                + "\",\"lastClockByPage\":{},\"currentPageId\":null}");

        String msg = bob.poll(5000);
        assertThat(msg).isNotNull();
        JsonNode node = objectMapper.readTree(msg);
        assertThat(node.path("type").asText()).isEqualTo("connected");
        assertThat(node.path("hydrationType").asText()).isEqualTo("full");

        JsonNode pages = node.path("pages");
        assertThat(pages.isArray()).isTrue();
        assertThat(pages.size()).isGreaterThan(0);

        JsonNode elements = pages.get(0).path("elements");
        assertThat(elements.isArray()).isTrue();
        assertThat(elements.size())
                .as("currentPageId=null로 connect해도 첫 번째 페이지 elements가 포함되어야 함")
                .isGreaterThanOrEqualTo(3);
    }

    // ────────────────────────────────────────────
    // WS 연결 헬퍼
    // ────────────────────────────────────────────

    private WsConn connect(String albumId, String token) throws Exception {
        WsConn conn = new WsConn();
        String url = "ws://localhost:" + port + "/sync/excalidraw/" + albumId + "?token=" + token;
        new StandardWebSocketClient()
                .execute(conn, new WebSocketHttpHeaders(), URI.create(url))
                .get(5, TimeUnit.SECONDS);
        openConnections.add(conn);
        return conn;
    }

    // ────────────────────────────────────────────
    // REST 헬퍼
    // ────────────────────────────────────────────

    private String base() {
        return "http://localhost:" + port;
    }

    private String testLogin(String email, String nickname) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"nickname\":\"" + nickname + "\"}";
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/auth/test-login", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        return objectMapper.readTree(resp.getBody()).path("data").path("accessToken").asText();
    }

    private String createAlbum(String token, String name) throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/albums", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"" + name + "\"}", authJson(token)), String.class);
        return objectMapper.readTree(resp.getBody()).path("data").path("id").asText();
    }

    private String getFirstPageId(String token, String albumId) throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/albums/" + albumId + "/pages", HttpMethod.GET,
                new HttpEntity<>(auth(token)), String.class);
        JsonNode pages = objectMapper.readTree(resp.getBody()).path("data");
        return pages.get(0).path("pageId").asText();
    }

    private String createInvite(String token, String albumId, String role, boolean approval) throws Exception {
        String body = "{\"role\":\"" + role + "\",\"approvalRequired\":" + approval + "}";
        ResponseEntity<String> resp = restTemplate.exchange(
                base() + "/albums/" + albumId + "/invite", HttpMethod.POST,
                new HttpEntity<>(body, authJson(token)), String.class);
        return objectMapper.readTree(resp.getBody()).path("data").path("code").asText();
    }

    private void joinInvite(String token, String code) {
        restTemplate.exchange(
                base() + "/invite/" + code + "/join", HttpMethod.POST,
                new HttpEntity<>(auth(token)), String.class);
    }

    private void patchAlbum(String token, String albumId, String body) {
        restTemplate.exchange(
                base() + "/albums/" + albumId, HttpMethod.PATCH,
                new HttpEntity<>(body, authJson(token)), String.class);
    }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + token);
        return h;
    }

    private HttpHeaders authJson(String token) {
        HttpHeaders h = auth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String extractUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = parts[1];
            int pad = (4 - payload.length() % 4) % 4;
            byte[] decoded = Base64.getUrlDecoder().decode(payload + "=".repeat(pad));
            return objectMapper.readTree(decoded).path("sub").asText();
        } catch (Exception e) {
            return null;
        }
    }

    // ────────────────────────────────────────────
    // 메시지 빌더 헬퍼
    // ────────────────────────────────────────────

    /** connect 메시지 — 토큰을 본문에 포함 (핸들러 인증 방식과 동일) */
    private String connectMsg(String token) {
        return "{\"type\":\"connect\",\"token\":\"" + token + "\",\"lastClockByPage\":{}}";
    }

    private String buildPush(String pageId, long clock, String elements) {
        return "{\"type\":\"push\",\"pageId\":\"" + pageId
                + "\",\"clientClock\":" + clock
                + ",\"elements\":" + elements + "}";
    }

    private String singleElement(String id, int version) {
        return "[{\"id\":\"" + id + "\",\"type\":\"rectangle\","
                + "\"version\":" + version + ",\"versionNonce\":" + version + ","
                + "\"isDeleted\":false,\"x\":0,\"y\":0,\"width\":100,\"height\":100}]";
    }

    // ────────────────────────────────────────────
    // WS 연결 클래스
    // ────────────────────────────────────────────

    static class WsConn extends TextWebSocketHandler {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private WebSocketSession session;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            this.session = session;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            queue.offer(message.getPayload());
        }

        String poll(long timeoutMs) throws InterruptedException {
            return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        void close() throws Exception {
            if (session != null && session.isOpen()) session.close();
        }
    }
}
