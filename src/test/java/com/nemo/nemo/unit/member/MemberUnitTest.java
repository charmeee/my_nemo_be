package com.nemo.nemo.unit.member;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1-E 멤버 관리 단위 테스트 — E2E에서 누락된 항목
 * TC-MEMBER-01, TC-MEMBER-04, TC-MEMBER-06, TC-MEMBER-08, TC-MEMBER-10, TC-MEMBER-11, TC-MEMBER-16
 */
@DisplayName("1-E Member Unit Tests")
class MemberUnitTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-MEMBER-01: 멤버 목록 조회 → ACTIVE 멤버만 포함")
    void member01_memberList_activeOnly() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 멤버목록");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Carol: approvalRequired → PENDING (목록에 미포함)
        joinViaInvite(carolToken, createInviteLink(aliceToken, albumId, "VIEWER", true));

        var resp = get("/albums/" + albumId + "/members", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);

        var data = json(resp).path("data");
        assertThat(data.isArray()).isTrue();

        // ACTIVE 멤버: Alice, Bob (Carol은 PENDING이므로 미포함)
        boolean hasAlice = false, hasBob = false, hasCarol = false;
        for (var m : data) {
            String uid = m.path("userId").asText();
            if (aliceId.equals(uid)) hasAlice = true;
            if (bobId.equals(uid)) hasBob = true;
            if (carolId.equals(uid)) hasCarol = true;
        }
        assertThat(hasAlice).isTrue();
        assertThat(hasBob).isTrue();
        assertThat(hasCarol).isFalse();
    }

    @Test
    @DisplayName("TC-MEMBER-04: 참여 요청 거절 → REJECTED, joined에 미등장")
    void member04_rejectJoin_notInJoined() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 거절테스트");
        String code = createInviteLink(aliceToken, albumId, "EDITOR", true);
        joinViaInvite(bobToken, code);

        // 거절
        assertThat(statusOf(post("/albums/" + albumId + "/members/" + bobId + "/reject",
                aliceToken, null))).isEqualTo(200);

        // Bob joined 목록에 없음
        var albums = json(get("/albums", bobToken)).path("data").path("joined");
        boolean bobInJoined = false;
        for (var a : albums) {
            if (albumId.equals(a.path("id").asText())) { bobInJoined = true; break; }
        }
        assertThat(bobInJoined).isFalse();

        // Bob이 앨범 접근 → 403
        assertThat(statusOf(get("/albums/" + albumId, bobToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-MEMBER-06: VIEWER → EDITOR 역할 상향 → 200, 편집 가능")
    void member06_viewerToEditor_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 역할상향");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "VIEWER", false));

        // VIEWER → EDITOR
        var resp = patch("/albums/" + albumId + "/members/" + bobId, aliceToken, "{\"role\":\"EDITOR\"}");
        assertThat(statusOf(resp)).isEqualTo(200);

        // Bob이 이미지 업로드 가능 (EDITOR 권한 확인)
        assertThat(statusOf(upload("/albums/" + albumId + "/images", bobToken, fakejpeg("file")))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-MEMBER-08: 자기 자신 역할 변경 시도 → 400/403")
    void member08_selfRoleChange_rejected() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 자기역할변경");

        // ADMIN이 자기 자신 역할 변경 시도
        int status = statusOf(patch("/albums/" + albumId + "/members/" + aliceId, aliceToken, "{\"role\":\"EDITOR\"}"));
        assertThat(status).isIn(400, 403);
    }

    @Test
    @DisplayName("TC-MEMBER-10: 자기 자신 추방 시도 → 400/403")
    void member10_selfKick_rejected() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 자기추방");

        int status = statusOf(delete("/albums/" + albumId + "/members/" + aliceId, aliceToken));
        assertThat(status).isIn(400, 403);
    }

    @Test
    @DisplayName("TC-MEMBER-11: EDITOR가 다른 멤버 추방 시도 → 403")
    void member11_editorKick_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 에디터추방");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));
        joinViaInvite(carolToken, createInviteLink(aliceToken, albumId, "VIEWER", false));

        // Bob(EDITOR)이 Carol 추방 시도 → 403
        assertThat(statusOf(delete("/albums/" + albumId + "/members/" + carolId, bobToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-MEMBER-16: EDITOR 탈퇴 후 앨범에 더 이상 접근 불가")
    void member16_editorLeave_noAccess() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 에디터탈퇴");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Bob 탈퇴
        assertThat(statusOf(delete("/albums/" + albumId + "/members/me", bobToken))).isEqualTo(200);

        // Bob joined에서 제거
        var joined = json(get("/albums", bobToken)).path("data").path("joined");
        boolean stillJoined = false;
        for (var a : joined) {
            if (albumId.equals(a.path("id").asText())) { stillJoined = true; break; }
        }
        assertThat(stillJoined).isFalse();

        // Bob 앨범 접근 → 403
        assertThat(statusOf(get("/albums/" + albumId, bobToken))).isEqualTo(403);
    }
}
