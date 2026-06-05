package com.nemo.nemo.unit.page;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1-C 페이지 단위 테스트 — E2E에서 누락된 항목
 * TC-PAGE-01, TC-PAGE-06, TC-PAGE-07
 */
@DisplayName("1-C Page Unit Tests")
class PageUnitTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-PAGE-01: 페이지 목록 조회 → 200 + 기본 페이지 1개 포함")
    void page01_getPages_200_hasDefaultPage() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 페이지목록");

        var resp = get("/albums/" + albumId + "/pages", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);

        var data = json(resp).path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(1);
        // 기본 페이지: pageOrder = 1
        assertThat(data.get(0).path("pageOrder").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-PAGE-06: VIEWER가 페이지 삭제 시도 → 403")
    void page06_viewerDeletePage_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 뷰어페이지삭제");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "VIEWER", false));

        // 페이지 추가 (Alice가)
        var addResp = post("/albums/" + albumId + "/pages", aliceToken, "{\"name\":\"삭제대상페이지\"}");
        String pageId = json(addResp).path("data").path("pageId").asText();

        // Bob(VIEWER)이 삭제 시도
        assertThat(statusOf(delete("/albums/" + albumId + "/pages/" + pageId, bobToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-PAGE-07: 페이지 순서 변경 → 200 + pageOrder 갱신")
    void page07_pageOrderChange_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 페이지순서");

        // 페이지 추가
        var addResp = post("/albums/" + albumId + "/pages", aliceToken, "{\"name\":\"두번째페이지\"}");
        String pageId = json(addResp).path("data").path("pageId").asText();

        // 순서를 1로 변경 (맨 앞으로)
        var patchResp = patch("/albums/" + albumId + "/pages/" + pageId, aliceToken, "{\"pageOrder\":1}");
        assertThat(statusOf(patchResp)).isEqualTo(200);
        assertThat(json(patchResp).path("data").path("pageOrder").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-PAGE-06b: EDITOR는 자신이 아닌 페이지 삭제 가능 → 200")
    void page06b_editorDeletePage_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 에디터페이지삭제");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        var addResp = post("/albums/" + albumId + "/pages", aliceToken, "{\"name\":\"에디터삭제가능\"}");
        String pageId = json(addResp).path("data").path("pageId").asText();

        assertThat(statusOf(delete("/albums/" + albumId + "/pages/" + pageId, bobToken))).isEqualTo(200);
    }
}
