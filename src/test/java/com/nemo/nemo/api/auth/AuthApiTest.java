package com.nemo.nemo.api.auth;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-A. 인증 (Auth) API E2E 테스트
 * TC-API-E2E-AUTH-01 ~ 06
 */
@DisplayName("7-A Auth API E2E")
class AuthApiTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-API-E2E-AUTH-01: 신규 이메일 test-login → accessToken 발급")
    void auth01_newUser_testLogin_returnsAccessToken() throws Exception {
        String email = "newuser-" + UUID.randomUUID() + "@test.com";
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("email", email, "nickname", "뉴유저"));

        ResponseEntity<String> resp = postNoAuth("/auth/test-login", body);
        assertThat(statusOf(resp)).isEqualTo(200);

        var node = json(resp);
        assertThat(node.path("success").asBoolean()).isTrue();
        String accessToken = node.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(accessToken.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-02: 동일 이메일 재로그인 → member 중복 생성 없음")
    void auth02_sameEmail_relogin_noDuplicate() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("email", email, "nickname", "중복유저"));

        ResponseEntity<String> r1 = postNoAuth("/auth/test-login", body);
        ResponseEntity<String> r2 = postNoAuth("/auth/test-login", body);

        String token1 = json(r1).path("data").path("accessToken").asText();
        String token2 = json(r2).path("data").path("accessToken").asText();

        assertThat(extractUserIdFromToken(token1)).isEqualTo(extractUserIdFromToken(token2));
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-03: 유효한 토큰으로 GET /albums → 200")
    void auth03_validToken_getAlbums_200() throws Exception {
        assertThat(statusOf(get("/albums", aliceToken))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-04: 토큰 없이 보호 API → 401")
    void auth04_noToken_401() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity(url("/albums"), String.class);
        assertThat(statusOf(resp)).isEqualTo(401);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-05: 잘못된 토큰 → 401")
    void auth05_invalidToken_401() throws Exception {
        ResponseEntity<String> resp = get("/albums", "invalid.token.here");
        assertThat(statusOf(resp)).isEqualTo(401);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-06: 로그아웃 후 기존 토큰 재사용 → 401")
    void auth06_afterLogout_tokenReuse_401() throws Exception {
        assertThat(statusOf(get("/albums", aliceToken))).isEqualTo(200);

        assertThat(statusOf(post("/auth/logout", aliceToken, null))).isEqualTo(200);

        assertThat(statusOf(get("/albums", aliceToken))).isEqualTo(401);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-07: GET /auth/me → 자기 정보 반환")
    void auth07_me_returnsSelfInfo() throws Exception {
        var resp = get("/auth/me", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);
        var data = json(resp).path("data");
        assertThat(data.path("id").asText()).isEqualTo(aliceId);
        assertThat(data.path("nickname").asText()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-08: register 신규 이메일 → 토큰 + Set-Cookie(refreshToken)")
    void auth08_register_success_setsCookie() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@test.com";
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "email", email, "password", "passW0rd!", "nickname", "Reg"));

        var resp = postNoAuth("/auth/register", body);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("accessToken").asText()).isNotBlank();
        assertThat(resp.getHeaders().get("Set-Cookie")).isNotNull();
        assertThat(resp.getHeaders().get("Set-Cookie").stream()
                .anyMatch(c -> c.startsWith("refreshToken="))).isTrue();
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-09: register 중복 이메일 → 409 EMAIL_ALREADY_EXISTS")
    void auth09_register_duplicateEmail_409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "email", email, "password", "passW0rd!", "nickname", "Dup"));

        assertThat(statusOf(postNoAuth("/auth/register", body))).isEqualTo(200);
        assertThat(statusOf(postNoAuth("/auth/register", body))).isEqualTo(409);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-10: login 성공 → accessToken 반환")
    void auth10_login_success() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@test.com";
        String regBody = objectMapper.writeValueAsString(java.util.Map.of(
                "email", email, "password", "passW0rd!", "nickname", "Login"));
        postNoAuth("/auth/register", regBody);

        String loginBody = objectMapper.writeValueAsString(java.util.Map.of(
                "email", email, "password", "passW0rd!"));
        var resp = postNoAuth("/auth/login", loginBody);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-11: login 잘못된 비밀번호 → 401 INVALID_CREDENTIALS")
    void auth11_login_wrongPassword_401() throws Exception {
        String email = "wrongpw-" + UUID.randomUUID() + "@test.com";
        String regBody = objectMapper.writeValueAsString(java.util.Map.of(
                "email", email, "password", "passW0rd!", "nickname", "Wrong"));
        postNoAuth("/auth/register", regBody);

        String loginBody = objectMapper.writeValueAsString(java.util.Map.of(
                "email", email, "password", "wrongPW"));
        assertThat(statusOf(postNoAuth("/auth/login", loginBody))).isEqualTo(401);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-12: login 존재하지 않는 이메일 → 401 INVALID_CREDENTIALS")
    void auth12_login_unknownEmail_401() throws Exception {
        String loginBody = objectMapper.writeValueAsString(java.util.Map.of(
                "email", "nobody-" + UUID.randomUUID() + "@test.com",
                "password", "anything"));
        assertThat(statusOf(postNoAuth("/auth/login", loginBody))).isEqualTo(401);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-13: refresh — 쿠키 없으면 4xx REFRESH_TOKEN_NOT_FOUND")
    void auth13_refresh_noCookie_4xx() throws Exception {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var resp = rest.exchange(url("/auth/refresh"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(null, h),
                String.class);
        assertThat(statusOf(resp)).isBetween(400, 499);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-14: refresh — 잘못된 쿠키 토큰 → 4xx INVALID_TOKEN")
    void auth14_refresh_invalidCookie_4xx() throws Exception {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        h.add("Cookie", "refreshToken=garbage.value.here");
        var resp = rest.exchange(url("/auth/refresh"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(null, h),
                String.class);
        assertThat(statusOf(resp)).isBetween(400, 499);
    }

    @Test
    @DisplayName("TC-API-E2E-AUTH-15: refresh — register로 받은 쿠키로 새 accessToken 발급")
    void auth15_refresh_validCookie_returnsNewToken() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@test.com";
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "email", email, "password", "passW0rd!", "nickname", "Refresh"));
        var regResp = postNoAuth("/auth/register", body);
        String refreshCookie = regResp.getHeaders().get("Set-Cookie").stream()
                .filter(c -> c.startsWith("refreshToken="))
                .map(c -> c.split(";")[0])
                .findFirst().orElseThrow();

        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        h.add("Cookie", refreshCookie);
        var resp = rest.exchange(url("/auth/refresh"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(null, h),
                String.class);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("accessToken").asText()).isNotBlank();
    }
}
