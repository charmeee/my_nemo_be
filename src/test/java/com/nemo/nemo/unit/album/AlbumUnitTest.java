package com.nemo.nemo.unit.album;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1-B 앨범 CRUD 단위 테스트 — E2E에서 누락된 항목
 * TC-ALBUM-11, TC-ALBUM-14
 */
@DisplayName("1-B Album Unit Tests")
class AlbumUnitTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-ALBUM-11: ADMIN이 잠금 OFF → 200, isLocked=false")
    void album11_adminUnlock_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 잠금해제");

        // 잠금 ON
        assertThat(statusOf(patch("/albums/" + albumId, aliceToken, "{\"isLocked\":true}"))).isEqualTo(200);

        // 잠금 OFF
        var resp = patch("/albums/" + albumId, aliceToken, "{\"isLocked\":false}");
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("isLocked").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("TC-ALBUM-14: EDITOR가 앨범 삭제 시도 → 403")
    void album14_editorDelete_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 에디터삭제시도");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        assertThat(statusOf(delete("/albums/" + albumId, bobToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-ALBUM-14b: VIEWER가 앨범 삭제 시도 → 403")
    void album14b_viewerDelete_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 뷰어삭제시도");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "VIEWER", false));

        assertThat(statusOf(delete("/albums/" + albumId, bobToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-ALBUM-15: 앨범 생성 시 초대 링크 자동 발급 확인")
    void album15_autoInviteLink_onCreate() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 자동초대링크");

        var resp = get("/albums/" + albumId + "/invite", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").isArray()).isTrue();
        assertThat(json(resp).path("data").size()).isGreaterThanOrEqualTo(1);
    }
}
