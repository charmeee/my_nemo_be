package com.nemo.nemo.api.trash;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-H. 휴지통 API E2E 테스트
 * TC-API-E2E-TRASH-01 ~ 04
 */
@DisplayName("7-H Trash API E2E")
class TrashApiTest extends ApiE2ETestBase {

    private String deleteAlbumAndGetTrashId(String token, String albumId) throws Exception {
        assertThat(statusOf(delete("/albums/" + albumId, token))).isEqualTo(200);

        var items = json(get("/trash", token)).path("data");
        for (var item : items) {
            if (albumId.equals(item.path("referenceId").asText())) {
                return item.path("id").asText();
            }
        }
        return null;
    }

    @Test
    @DisplayName("TC-API-E2E-TRASH-01: 삭제된 앨범과 페이지 모두 GET /trash에 표시")
    void trash01_deletedAlbumAndPage_inTrash() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 휴지통테스트");

        // 페이지 추가 후 삭제
        var addPage = post("/albums/" + albumId + "/pages", aliceToken, "{\"name\":\"삭제될페이지\"}");
        String pageId = json(addPage).path("data").path("pageId").asText();
        assertThat(statusOf(delete("/albums/" + albumId + "/pages/" + pageId, aliceToken))).isEqualTo(200);

        // 앨범 삭제
        assertThat(statusOf(delete("/albums/" + albumId, aliceToken))).isEqualTo(200);

        // 휴지통에 앨범과 페이지 모두 존재
        var items = json(get("/trash", aliceToken)).path("data");
        boolean hasAlbum = false, hasPage = false;
        for (var item : items) {
            String type = item.path("type").asText();
            String refId = item.path("referenceId").asText();
            if ("ALBUM".equalsIgnoreCase(type) && albumId.equals(refId)) hasAlbum = true;
            if ("PAGE".equalsIgnoreCase(type) && pageId.equals(refId)) hasPage = true;
        }
        assertThat(hasAlbum).isTrue();
        assertThat(hasPage).isTrue();
    }

    @Test
    @DisplayName("TC-API-E2E-TRASH-02: 앨범 복원 → 목록 재등장 + 멤버십 유지")
    void trash02_restoreAlbum_appearsInList_membershipPreserved() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 복원테스트");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        String trashId = deleteAlbumAndGetTrashId(aliceToken, albumId);
        assertThat(trashId).isNotNull();

        assertThat(statusOf(post("/trash/" + trashId + "/restore", aliceToken, null))).isEqualTo(200);

        // Alice owned에 복원
        var aliceOwned = json(get("/albums", aliceToken)).path("data").path("owned");
        boolean aliceFound = false;
        for (var item : aliceOwned) {
            if (albumId.equals(item.path("id").asText())) { aliceFound = true; break; }
        }
        assertThat(aliceFound).isTrue();

        // Bob joined에 복원 (멤버십 유지)
        var bobJoined = json(get("/albums", bobToken)).path("data").path("joined");
        boolean bobFound = false;
        for (var item : bobJoined) {
            if (albumId.equals(item.path("id").asText())) { bobFound = true; break; }
        }
        assertThat(bobFound).isTrue();
    }

    @Test
    @DisplayName("TC-API-E2E-TRASH-03: 영구 삭제 후 복원 불가")
    void trash03_permanentDelete_cannotRestore() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 영구삭제");

        String trashId = deleteAlbumAndGetTrashId(aliceToken, albumId);
        assertThat(trashId).isNotNull();

        // 영구 삭제
        assertThat(statusOf(delete("/trash/" + trashId, aliceToken))).isEqualTo(200);

        // 휴지통에 없음
        var items = json(get("/trash", aliceToken)).path("data");
        boolean found = false;
        for (var item : items) {
            if (trashId.equals(item.path("id").asText())) { found = true; break; }
        }
        assertThat(found).isFalse();

        // 앨범 접근 → 404
        assertThat(statusOf(get("/albums/" + albumId, aliceToken))).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-API-E2E-TRASH-04: 타인 휴지통 복원/삭제 → 403")
    void trash04_otherUserTrash_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 타인휴지통");
        String trashId = deleteAlbumAndGetTrashId(aliceToken, albumId);
        assertThat(trashId).isNotNull();

        assertThat(statusOf(post("/trash/" + trashId + "/restore", bobToken, null))).isEqualTo(403);
        assertThat(statusOf(delete("/trash/" + trashId, bobToken))).isEqualTo(403);
    }
}
