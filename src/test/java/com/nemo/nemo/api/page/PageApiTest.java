package com.nemo.nemo.api.page;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-C. 페이지 관리 API E2E 테스트
 * TC-API-E2E-PAGE-01 ~ 05
 */
@DisplayName("7-C Page API E2E")
class PageApiTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-API-E2E-PAGE-01: EDITOR 페이지 추가 → 200")
    void page01_editorAddPage_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] EDITOR페이지테스트");

        String code = createInviteLink(aliceToken, albumId, "EDITOR", false);
        joinViaInvite(bobToken, code);

        String body = objectMapper.writeValueAsString(Map.of("name", "Page 2"));
        var resp = post("/albums/" + albumId + "/pages", bobToken, body);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("pageId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("TC-API-E2E-PAGE-02: VIEWER 페이지 추가 → 403")
    void page02_viewerAddPage_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] VIEWER페이지테스트");

        String code = createInviteLink(aliceToken, albumId, "VIEWER", false);
        joinViaInvite(carolToken, code);

        String body = objectMapper.writeValueAsString(Map.of("name", "Page X"));
        assertThat(statusOf(post("/albums/" + albumId + "/pages", carolToken, body))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-PAGE-03: 30페이지 한도 초과 → 400")
    void page03_pageLimit_400() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 한도테스트");

        for (int i = 2; i <= 30; i++) {
            String body = objectMapper.writeValueAsString(Map.of("name", "Page " + i));
            assertThat(statusOf(post("/albums/" + albumId + "/pages", aliceToken, body))).isEqualTo(200);
        }

        String body = objectMapper.writeValueAsString(Map.of("name", "Page 31"));
        assertThat(statusOf(post("/albums/" + albumId + "/pages", aliceToken, body))).isEqualTo(400);
    }

    @Test
    @DisplayName("TC-API-E2E-PAGE-04: 페이지 삭제 → 휴지통, 복원 후 목록 재등장")
    void page04_delete_trash_restore() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 페이지삭제복원");

        String body = objectMapper.writeValueAsString(Map.of("name", "삭제될 페이지"));
        var addResp = post("/albums/" + albumId + "/pages", aliceToken, body);
        String pageId = json(addResp).path("data").path("pageId").asText();

        // 삭제
        assertThat(statusOf(delete("/albums/" + albumId + "/pages/" + pageId, aliceToken))).isEqualTo(200);

        // 페이지 목록에 없어야 함
        var pages = json(get("/albums/" + albumId + "/pages", aliceToken)).path("data");
        boolean deleted = false;
        for (var p : pages) {
            if (pageId.equals(p.path("pageId").asText())) { deleted = true; break; }
        }
        assertThat(deleted).isFalse();

        // 휴지통에 있어야 함 + trashId 확보
        var trashItems = json(get("/trash", aliceToken)).path("data");
        String trashId = null;
        for (var item : trashItems) {
            if (pageId.equals(item.path("referenceId").asText())) {
                trashId = item.path("id").asText(); break;
            }
        }
        assertThat(trashId).isNotNull();

        // 복원
        assertThat(statusOf(post("/trash/" + trashId + "/restore", aliceToken, null))).isEqualTo(200);

        // 복원 후 다시 목록에 있어야 함
        var restoredPages = json(get("/albums/" + albumId + "/pages", aliceToken)).path("data");
        boolean restored = false;
        for (var p : restoredPages) {
            if (pageId.equals(p.path("pageId").asText())) { restored = true; break; }
        }
        assertThat(restored).isTrue();
    }

    @Test
    @DisplayName("TC-API-E2E-PAGE-05: 30개 꽉 찬 상태에서 1개 삭제 후 추가 → 200")
    void page05_deleteAndAddAfterLimit() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 삭제후추가");

        String firstExtraPageId = null;
        for (int i = 2; i <= 30; i++) {
            String body = objectMapper.writeValueAsString(Map.of("name", "P" + i));
            var r = post("/albums/" + albumId + "/pages", aliceToken, body);
            if (i == 2) {
                firstExtraPageId = json(r).path("data").path("pageId").asText();
            }
        }

        // 31번째 → 400
        String body31 = objectMapper.writeValueAsString(Map.of("name", "P31"));
        assertThat(statusOf(post("/albums/" + albumId + "/pages", aliceToken, body31))).isEqualTo(400);

        // 1개 삭제 후 추가 → 200
        assertThat(statusOf(delete("/albums/" + albumId + "/pages/" + firstExtraPageId, aliceToken))).isEqualTo(200);

        String bodyNew = objectMapper.writeValueAsString(Map.of("name", "새 페이지"));
        assertThat(statusOf(post("/albums/" + albumId + "/pages", aliceToken, bodyNew))).isEqualTo(200);
    }
}
