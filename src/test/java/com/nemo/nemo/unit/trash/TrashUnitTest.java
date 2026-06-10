package com.nemo.nemo.unit.trash;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1-H 휴지통 단위 테스트 — E2E에서 누락된 항목
 * TC-TRASH-03 (페이지 복원)
 */
@DisplayName("1-H Trash Unit Tests")
class TrashUnitTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-TRASH-03: 삭제된 페이지 복원 → 페이지 목록 재등장")
    void trash03_deletedPageRestore_appearsInPages() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 페이지복원");

        // 페이지 추가 후 삭제
        var addResp = post("/albums/" + albumId + "/pages", aliceToken, "{\"name\":\"복원될페이지\"}");
        String pageId = json(addResp).path("data").path("pageId").asText();
        assertThat(statusOf(delete("/albums/" + albumId + "/pages/" + pageId, aliceToken))).isEqualTo(200);

        // 휴지통에서 trashId 확인
        String trashId = null;
        for (var item : json(get("/trash", aliceToken)).path("data")) {
            if (pageId.equals(item.path("referenceId").asText())) {
                trashId = item.path("id").asText();
                break;
            }
        }
        assertThat(trashId).isNotNull();

        // 복원
        assertThat(statusOf(post("/trash/" + trashId + "/restore", aliceToken, null))).isEqualTo(200);

        // 페이지 목록에 재등장
        var pages = json(get("/albums/" + albumId + "/pages", aliceToken)).path("data");
        boolean restored = false;
        for (var p : pages) {
            if (pageId.equals(p.path("pageId").asText())) { restored = true; break; }
        }
        assertThat(restored).isTrue();
    }

    @Test
    @DisplayName("TC-TRASH-03b: 복원된 페이지에서 편집 가능 → elements patch 200")
    void trash03b_restoredPage_editableAgain() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 복원편집");

        var addResp = post("/albums/" + albumId + "/pages", aliceToken, "{\"name\":\"복원후편집\"}");
        String pageId = json(addResp).path("data").path("pageId").asText();

        delete("/albums/" + albumId + "/pages/" + pageId, aliceToken);

        String trashId = null;
        for (var item : json(get("/trash", aliceToken)).path("data")) {
            if (pageId.equals(item.path("referenceId").asText())) {
                trashId = item.path("id").asText(); break;
            }
        }
        post("/trash/" + trashId + "/restore", aliceToken, null);

        // 복원 후 페이지 elements 업데이트 → 200
        var patchResp = patch("/albums/" + albumId + "/pages/" + pageId, aliceToken,
                "{\"elements\": \"[]\"}");
        assertThat(statusOf(patchResp)).isEqualTo(200);
    }
}
