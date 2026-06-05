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
}
