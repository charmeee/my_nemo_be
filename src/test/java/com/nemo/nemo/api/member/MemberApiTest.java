package com.nemo.nemo.api.member;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-E. 멤버 관리 API E2E 테스트
 * TC-API-E2E-MEMBER-01 ~ 08
 */
@DisplayName("7-E Member API E2E")
class MemberApiTest extends ApiE2ETestBase {

    private String setupAlbumWithMembers() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 멤버관리");

        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));
        joinViaInvite(carolToken, createInviteLink(aliceToken, albumId, "VIEWER", false));

        return albumId;
    }

    @Test
    @DisplayName("TC-API-E2E-MEMBER-01: ADMIN이 EDITOR → VIEWER 역할 변경 → 200")
    void member01_adminChangeRole_editorToViewer() throws Exception {
        String albumId = setupAlbumWithMembers();

        String body = objectMapper.writeValueAsString(Map.of("role", "VIEWER"));
        assertThat(statusOf(patch("/albums/" + albumId + "/members/" + bobId, aliceToken, body))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-API-E2E-MEMBER-02: EDITOR가 역할 변경 시도 → 403")
    void member02_editorChangeRole_403() throws Exception {
        String albumId = setupAlbumWithMembers();

        String body = objectMapper.writeValueAsString(Map.of("role", "EDITOR"));
        assertThat(statusOf(patch("/albums/" + albumId + "/members/" + carolId, bobToken, body))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-MEMBER-03: ADMIN으로 직접 역할 변경 → 403")
    void member03_changeRoleToAdmin_403() throws Exception {
        String albumId = setupAlbumWithMembers();

        String body = objectMapper.writeValueAsString(Map.of("role", "ADMIN"));
        assertThat(statusOf(patch("/albums/" + albumId + "/members/" + bobId, aliceToken, body))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-MEMBER-04: 관리자 이관 후 역할 전환 확인")
    void member04_transferAdmin_roleSwap() throws Exception {
        String albumId = setupAlbumWithMembers();

        // Alice → Bob으로 ADMIN 이관
        String transferBody = objectMapper.writeValueAsString(Map.of("targetUserId", bobId));
        assertThat(statusOf(post("/albums/" + albumId + "/members/transfer", aliceToken, transferBody))).isEqualTo(200);

        // Alice(이제 EDITOR)가 앨범 이름 변경 시도 → 403
        String renameBody = objectMapper.writeValueAsString(Map.of("name", "[TC] Alice시도"));
        assertThat(statusOf(patch("/albums/" + albumId, aliceToken, renameBody))).isEqualTo(403);

        // Bob(이제 ADMIN)이 앨범 이름 변경 → 200
        String bobRenameBody = objectMapper.writeValueAsString(Map.of("name", "[TC] Bob성공"));
        assertThat(statusOf(patch("/albums/" + albumId, bobToken, bobRenameBody))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-API-E2E-MEMBER-05: 멤버 추방 후 앨범 접근 → 403")
    void member05_kickMember_noAccess() throws Exception {
        String albumId = setupAlbumWithMembers();

        assertThat(statusOf(delete("/albums/" + albumId + "/members/" + bobId, aliceToken))).isEqualTo(200);
        assertThat(statusOf(get("/albums/" + albumId, bobToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-MEMBER-06: ADMIN 혼자 남은 상태 탈퇴 → 400")
    void member06_adminAloneLeave_400() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] ADMIN혼자탈퇴");
        assertThat(statusOf(delete("/albums/" + albumId + "/members/me", aliceToken))).isEqualTo(400);
    }

    @Test
    @DisplayName("TC-API-E2E-MEMBER-07: EDITOR 탈퇴 → 200, joined 목록에서 제거")
    void member07_editorLeave_removedFromJoined() throws Exception {
        String albumId = setupAlbumWithMembers();

        assertThat(statusOf(delete("/albums/" + albumId + "/members/me", bobToken))).isEqualTo(200);

        var joined = json(get("/albums", bobToken)).path("data").path("joined");
        boolean found = false;
        for (var item : joined) {
            if (albumId.equals(item.path("id").asText())) { found = true; break; }
        }
        assertThat(found).isFalse();
    }

    @Test
    @DisplayName("TC-API-E2E-MEMBER-08: 승인 후 PENDING → ACTIVE, joined 등장")
    void member08_approveAndJoined() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 승인테스트");
        String code = createInviteLink(aliceToken, albumId, "VIEWER", true);
        joinViaInvite(carolToken, code);

        assertThat(statusOf(post("/albums/" + albumId + "/members/" + carolId + "/approve",
                aliceToken, null))).isEqualTo(200);

        var joined = json(get("/albums", carolToken)).path("data").path("joined");
        boolean found = false;
        for (var item : joined) {
            if (albumId.equals(item.path("id").asText())) { found = true; break; }
        }
        assertThat(found).isTrue();
    }
}
