package com.nemo.nemo.api.invite;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-D. 초대 / 공유 API E2E 테스트
 * TC-API-E2E-INVITE-01 ~ 06
 */
@DisplayName("7-D Invite API E2E")
class InviteApiTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-API-E2E-INVITE-01: ADMIN 초대 링크 생성 → code 반환")
    void invite01_adminCreateLink_codeReturned() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 초대테스트");

        String body = objectMapper.writeValueAsString(Map.of("role", "EDITOR", "approvalRequired", false));
        var resp = post("/albums/" + albumId + "/invite", aliceToken, body);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("code").asText()).isNotBlank();
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-02: EDITOR가 초대 링크 생성 → 403")
    void invite02_editorCreateLink_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] EDITOR초대차단");

        String code = createInviteLink(aliceToken, albumId, "EDITOR", false);
        joinViaInvite(bobToken, code);

        String body = objectMapper.writeValueAsString(Map.of("role", "EDITOR", "approvalRequired", false));
        assertThat(statusOf(post("/albums/" + albumId + "/invite", bobToken, body))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-03: 자동참여 링크 join → ACTIVE 즉시, joined에 등장")
    void invite03_autoJoin_activeStatus_appearsInJoined() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 자동참여");
        String code = createInviteLink(aliceToken, albumId, "EDITOR", false);

        var joinResult = joinViaInvite(bobToken, code);
        assertThat(statusOf(joinResult)).isEqualTo(200);

        var joined = json(get("/albums", bobToken)).path("data").path("joined");
        boolean found = false;
        for (var item : joined) {
            if (albumId.equals(item.path("id").asText())) { found = true; break; }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-04: 승인 필요 링크 join → PENDING, joined에 미등장")
    void invite04_approvalRequired_pending_notInJoined() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 승인필요");
        String code = createInviteLink(aliceToken, albumId, "VIEWER", true);

        var joinResult = joinViaInvite(carolToken, code);
        assertThat(statusOf(joinResult)).isEqualTo(200);

        var joined = json(get("/albums", carolToken)).path("data").path("joined");
        boolean found = false;
        for (var item : joined) {
            if (albumId.equals(item.path("id").asText())) { found = true; break; }
        }
        assertThat(found).isFalse();
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-05: 비활성화 링크 join → 410")
    void invite05_inactiveLink_410() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 비활성화링크");

        String body = objectMapper.writeValueAsString(Map.of("role", "EDITOR", "approvalRequired", false));
        var createResp = post("/albums/" + albumId + "/invite", aliceToken, body);
        var linkJson = json(createResp).path("data");
        String code = linkJson.path("code").asText();
        String linkId = linkJson.path("id").asText();

        // 비활성화
        assertThat(statusOf(patch("/albums/" + albumId + "/invite/" + linkId + "?active=false",
                aliceToken, null))).isEqualTo(200);

        // 비활성화된 링크로 참여 → 410
        assertThat(statusOf(joinViaInvite(bobToken, code))).isEqualTo(410);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-06: 이미 멤버인 사용자 중복 join → 중복 row 없음")
    void invite06_duplicateJoin_noConflict() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 중복참여");
        String code = createInviteLink(aliceToken, albumId, "EDITOR", false);

        joinViaInvite(bobToken, code);
        var secondJoin = joinViaInvite(bobToken, code);
        assertThat(statusOf(secondJoin)).isIn(200, 409);

        var joined = json(get("/albums", bobToken)).path("data").path("joined");
        int count = 0;
        for (var item : joined) {
            if (albumId.equals(item.path("id").asText())) count++;
        }
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-07: GET /albums/{id}/invite — 초대 링크 목록 조회")
    void invite07_listInviteLinks_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 초대목록");
        createInviteLink(aliceToken, albumId, "EDITOR", false);
        createInviteLink(aliceToken, albumId, "VIEWER", true);

        var resp = get("/albums/" + albumId + "/invite", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);
        var data = json(resp).path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-08: GET /invite/{code}/info — 비로그인도 정보 조회 가능")
    void invite08_inviteInfo_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 초대정보");
        String code = createInviteLink(aliceToken, albumId, "EDITOR", true);

        var resp = get("/invite/" + code + "/info", bobToken);
        assertThat(statusOf(resp)).isEqualTo(200);
        var data = json(resp).path("data");
        assertThat(data.path("role").asText()).isEqualTo("EDITOR");
        assertThat(data.path("approvalRequired").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-09: GET /invite/{code}/info — 존재하지 않는 코드 → 404")
    void invite09_inviteInfo_notFound_404() throws Exception {
        var resp = get("/invite/nonexistent-" + UUID.randomUUID() + "/info", bobToken);
        assertThat(statusOf(resp)).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-10: POST /albums/{id}/invite/reissue — 새 코드 발급")
    void invite10_reissue_returnsNewCode() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 재발급");
        String originalCode = createInviteLink(aliceToken, albumId, "EDITOR", false);

        var resp = post("/albums/" + albumId + "/invite/reissue", aliceToken, null);
        assertThat(statusOf(resp)).isEqualTo(200);
        String newCode = json(resp).path("data").path("code").asText();
        assertThat(newCode).isNotBlank();
        assertThat(newCode).isNotEqualTo(originalCode);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-11: POST /invite/{code}/guest-session — guestToken 발급")
    void invite11_guestSession_returnsToken() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 게스트세션");
        String code = createInviteLink(aliceToken, albumId, "VIEWER", false);

        var resp = postNoAuth("/invite/" + code + "/guest-session", null);
        assertThat(statusOf(resp)).isEqualTo(200);
        var data = json(resp).path("data");
        assertThat(data.path("guestToken").asText()).isNotBlank();
        assertThat(data.path("albumId").asText()).isEqualTo(albumId);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-12: guest-session — 존재하지 않는 코드 → 404")
    void invite12_guestSession_notFound_404() throws Exception {
        var resp = postNoAuth("/invite/no-such-code/guest-session", null);
        assertThat(statusOf(resp)).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-13: guest-session — 비활성 링크 → 410")
    void invite13_guestSession_inactive_410() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 게스트비활성");
        String body = objectMapper.writeValueAsString(Map.of("role", "VIEWER", "approvalRequired", false));
        var createResp = post("/albums/" + albumId + "/invite", aliceToken, body);
        var linkJson = json(createResp).path("data");
        String code = linkJson.path("code").asText();
        String linkId = linkJson.path("id").asText();
        patch("/albums/" + albumId + "/invite/" + linkId + "?active=false", aliceToken, null);

        var resp = postNoAuth("/invite/" + code + "/guest-session", null);
        assertThat(statusOf(resp)).isEqualTo(410);
    }

    @Test
    @DisplayName("TC-API-E2E-INVITE-14: GET /invite/{code}/pages — 게스트가 페이지 목록 조회")
    void invite14_guestPages_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 게스트페이지");
        String code = createInviteLink(aliceToken, albumId, "VIEWER", false);

        org.springframework.http.ResponseEntity<String> resp = rest.exchange(
                url("/invite/" + code + "/pages"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(new org.springframework.http.HttpHeaders()),
                String.class);
        assertThat(statusOf(resp)).isEqualTo(200);
        var data = json(resp).path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(1);
    }
}
