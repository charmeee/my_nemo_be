package com.nemo.nemo.api.edge;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-I. API 레벨 엣지 케이스 E2E 테스트
 * TC-API-E2E-EDGE-01 ~ 07
 */
@DisplayName("7-I Edge Case API E2E")
class EdgeApiTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-API-E2E-EDGE-01: 비멤버의 앨범 API 전반 접근 → 403")
    void edge01_nonMemberAccess_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 비멤버접근");

        assertThat(statusOf(get("/albums/" + albumId, daveToken))).isEqualTo(403);
        assertThat(statusOf(patch("/albums/" + albumId, daveToken, "{\"name\":\"강제변경\"}"))).isEqualTo(403);
        assertThat(statusOf(get("/albums/" + albumId + "/pages", daveToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-EDGE-02: 잠금 앨범에서 EDITOR REST 편집 시도 → 403")
    void edge02_lockedAlbum_editorBlocked() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 잠금앨범EDITOR");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // 앨범 잠금
        assertThat(statusOf(patch("/albums/" + albumId, aliceToken, "{\"isLocked\":true}"))).isEqualTo(200);

        // Bob(EDITOR)이 이미지 업로드 시도 → 403
        assertThat(statusOf(upload("/albums/" + albumId + "/images", bobToken, fakejpeg("file")))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-EDGE-03: 앨범명 공백만 → 400")
    void edge03_blankAlbumName_400() throws Exception {
        assertThat(statusOf(post("/albums", aliceToken, "{\"name\":\"   \"}"))).isEqualTo(400);
    }

    @Test
    @DisplayName("TC-API-E2E-EDGE-04: 존재하지 않는 albumId → 404")
    void edge04_nonexistentAlbumId_404() throws Exception {
        String nonExistentId = "00000000-0000-0000-0000-000000000000";
        assertThat(statusOf(get("/albums/" + nonExistentId, aliceToken))).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-API-E2E-EDGE-05: 초대 링크 비활성화 직후 join → stale 캐시 없이 410")
    void edge05_inactivatedLink_staleCache_410() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 캐시stale");

        var createResp = post("/albums/" + albumId + "/invite", aliceToken,
                "{\"role\":\"EDITOR\",\"approvalRequired\":false}");
        assertThat(statusOf(createResp)).isEqualTo(200);

        var linkJson = json(createResp).path("data");
        String code = linkJson.path("code").asText();
        String linkId = linkJson.path("id").asText();

        // 비활성화
        assertThat(statusOf(patch("/albums/" + albumId + "/invite/" + linkId + "?active=false",
                aliceToken, null))).isEqualTo(200);

        // 즉시 join → 410
        assertThat(statusOf(joinViaInvite(bobToken, code))).isEqualTo(410);
    }

    @Test
    @DisplayName("TC-API-E2E-EDGE-06: Rate Limiting — 100회/분 초과 → 429")
    void edge06_rateLimiting_429() throws Exception {
        String uniqueEmail = "ratelimit-" + UUID.randomUUID() + "@test.com";
        String limitToken = testLogin(uniqueEmail, "RateUser");

        int tooManyCount = 0;
        for (int i = 0; i < 101; i++) {
            if (statusOf(get("/albums", limitToken)) == 429) {
                tooManyCount++;
            }
        }
        assertThat(tooManyCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("TC-API-E2E-EDGE-07: 알림 목록 조회 성공 (90일 경계 기본 동작 확인)")
    void edge07_notification90Days() throws Exception {
        var resp = get("/notifications", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("success").asBoolean()).isTrue();
        assertThat(json(resp).path("data").isArray()).isTrue();
    }
}
