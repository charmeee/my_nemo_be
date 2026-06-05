package com.nemo.nemo.api;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * API E2E 통합 테스트 기반 클래스.
 * - webEnvironment=RANDOM_PORT: 실제 Tomcat 기동, 실제 HTTP 소켓 통신
 * - @Transactional 없음: 서버-클라이언트 별도 스레드 → 트랜잭션 공유 불가
 * - 각 테스트는 고유 UUID suffix 이메일로 사용자를 생성하여 데이터 격리
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class ApiE2ETestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    protected RestTemplate rest;

    protected String aliceToken;
    protected String bobToken;
    protected String carolToken;
    protected String daveToken;

    protected String aliceId;
    protected String bobId;
    protected String carolId;
    protected String daveId;

    @BeforeEach
    void setupRestAndUsers() throws Exception {
        // 4xx/5xx 에서 예외를 던지지 않음 — 테스트에서 직접 상태코드 검사
        rest = new RestTemplate(new JdkClientHttpRequestFactory());
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            protected boolean hasError(HttpStatusCode statusCode) { return false; }
        });

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        aliceToken = testLogin("alice-" + suffix + "@test.com", "Alice");
        bobToken   = testLogin("bob-"   + suffix + "@test.com", "Bob");
        carolToken = testLogin("carol-" + suffix + "@test.com", "Carol");
        daveToken  = testLogin("dave-"  + suffix + "@test.com", "Dave");

        aliceId = extractUserIdFromToken(aliceToken);
        bobId   = extractUserIdFromToken(bobToken);
        carolId = extractUserIdFromToken(carolToken);
        daveId  = extractUserIdFromToken(daveToken);
    }

    // ── URL ──────────────────────────────────────────────────

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ── HTTP 헬퍼 ─────────────────────────────────────────────

    protected ResponseEntity<String> get(String path, String token) {
        return rest.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    protected ResponseEntity<String> post(String path, String token, String body) {
        HttpEntity<String> entity = body != null
                ? new HttpEntity<>(body, authJsonHeaders(token))
                : new HttpEntity<>(authHeaders(token));
        return rest.exchange(url(path), HttpMethod.POST, entity, String.class);
    }

    protected ResponseEntity<String> postNoAuth(String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
    }

    protected ResponseEntity<String> patch(String path, String token, String body) {
        HttpEntity<String> entity = body != null
                ? new HttpEntity<>(body, authJsonHeaders(token))
                : new HttpEntity<>(authHeaders(token));
        return rest.exchange(url(path), HttpMethod.PATCH, entity, String.class);
    }

    protected ResponseEntity<String> delete(String path, String token) {
        return rest.exchange(url(path), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

    protected ResponseEntity<String> upload(String path, String token, MockMultipartFile file) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);

        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception e) { throw new RuntimeException(e); }

        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return file.getOriginalFilename(); }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(file.getName(), new HttpEntity<>(resource, partHeaders));

        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    // ── 상태코드 / JSON 헬퍼 ───────────────────────────────────

    protected int statusOf(ResponseEntity<?> resp) {
        return resp.getStatusCode().value();
    }

    protected JsonNode json(ResponseEntity<String> resp) throws Exception {
        return objectMapper.readTree(resp.getBody());
    }

    protected JsonNode json(String s) throws Exception {
        return objectMapper.readTree(s);
    }

    // ── 테스트 데이터 헬퍼 ────────────────────────────────────

    protected String testLogin(String email, String nickname) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "nickname", nickname));
        ResponseEntity<String> resp = postNoAuth("/auth/test-login", body);
        return objectMapper.readTree(resp.getBody()).path("data").path("accessToken").asText();
    }

    protected String extractUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = parts[1];
            int padding = (4 - payload.length() % 4) % 4;
            payload = payload + "=".repeat(padding);
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            return objectMapper.readTree(decoded).path("sub").asText();
        } catch (Exception e) {
            return null;
        }
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String createAlbum(String token, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name));
        ResponseEntity<String> resp = post("/albums", token, body);
        return objectMapper.readTree(resp.getBody()).path("data").path("id").asText();
    }

    protected String createInviteLink(String token, String albumId, String role, boolean approvalRequired) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", role, "approvalRequired", approvalRequired));
        ResponseEntity<String> resp = post("/albums/" + albumId + "/invite", token, body);
        return objectMapper.readTree(resp.getBody()).path("data").path("code").asText();
    }

    protected ResponseEntity<String> joinViaInvite(String token, String code) {
        return post("/invite/" + code + "/join", token, null);
    }

    protected MockMultipartFile fakejpeg(String fieldName) {
        byte[] jpegBytes = new byte[]{
                (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte)0xFF, (byte)0xD9
        };
        return new MockMultipartFile(fieldName, "test.jpg", "image/jpeg", jpegBytes);
    }

    // ── 헤더 빌더 ─────────────────────────────────────────────

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + token);
        return h;
    }

    private HttpHeaders authJsonHeaders(String token) {
        HttpHeaders h = authHeaders(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
