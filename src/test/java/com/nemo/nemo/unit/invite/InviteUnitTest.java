package com.nemo.nemo.unit.invite;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1-D 초대 단위 테스트 — E2E에서 누락된 항목
 * TC-INVITE-10, TC-INVITE-11, TC-INVITE-14, TC-INVITE-15
 */
@DisplayName("1-D Invite Unit Tests")
class InviteUnitTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-INVITE-10: PENDING 상태 멤버가 동일 링크로 재참여 시도 → 중복 row 없음")
    void invite10_pendingRejoin_idempotent() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 대기재참여");
        String code = createInviteLink(aliceToken, albumId, "EDITOR", true); // approvalRequired

        // Bob: 첫 참여 → PENDING
        assertThat(statusOf(joinViaInvite(bobToken, code))).isEqualTo(200);

        // Bob: 같은 링크로 재참여 → 중복 없이 처리 (200 또는 409 중 하나 — 정합성 유지)
        int status = statusOf(joinViaInvite(bobToken, code));
        assertThat(status).isIn(200, 409);

        // 멤버 목록 PENDING에 Bob이 1번만 등장
        var pending = json(get("/albums/" + albumId + "/members?status=pending", aliceToken)).path("data");
        long bobCount = 0;
        for (var m : pending) {
            if (bobId.equals(m.path("userId").asText())) bobCount++;
        }
        assertThat(bobCount).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-INVITE-11: 링크 비활성화 후 재활성화 → join 성공")
    void invite11_deactivateAndReactivate_joinSucceeds() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 링크재활성");

        // 링크 생성
        var createResp = post("/albums/" + albumId + "/invite", aliceToken,
                "{\"role\":\"EDITOR\",\"approvalRequired\":false}");
        String code = json(createResp).path("data").path("code").asText();
        String linkId = json(createResp).path("data").path("id").asText();

        // 비활성화
        assertThat(statusOf(patch("/albums/" + albumId + "/invite/" + linkId + "?active=false",
                aliceToken, null))).isEqualTo(200);

        // Bob: 비활성 링크 join → 410
        assertThat(statusOf(joinViaInvite(bobToken, code))).isEqualTo(410);

        // 재활성화
        assertThat(statusOf(patch("/albums/" + albumId + "/invite/" + linkId + "?active=true",
                aliceToken, null))).isEqualTo(200);

        // Bob: 다시 join → 200
        assertThat(statusOf(joinViaInvite(bobToken, code))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-INVITE-14: 초대 링크 재발급 → 새 코드 반환, 기존 코드 무효화(410)")
    void invite14_reissue_newCodeOldInvalid() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 링크재발급");
        String oldCode = createInviteLink(aliceToken, albumId, "EDITOR", false);

        // 재발급
        var reissueResp = post("/albums/" + albumId + "/invite/reissue", aliceToken, null);
        assertThat(statusOf(reissueResp)).isEqualTo(200);
        String newCode = json(reissueResp).path("data").path("code").asText();

        assertThat(newCode).isNotBlank();
        assertThat(newCode).isNotEqualTo(oldCode);

        // 기존 코드로 join → 410
        assertThat(statusOf(joinViaInvite(bobToken, oldCode))).isEqualTo(410);

        // 새 코드로 join → 200
        assertThat(statusOf(joinViaInvite(carolToken, newCode))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-INVITE-15: VIEWER가 초대 링크 생성 시도 → 403")
    void invite15_viewerCreateInvite_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 뷰어초대생성");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "VIEWER", false));

        var resp = post("/albums/" + albumId + "/invite", bobToken,
                "{\"role\":\"EDITOR\",\"approvalRequired\":false}");
        assertThat(statusOf(resp)).isEqualTo(403);
    }
}
