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
}
