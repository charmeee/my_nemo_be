package com.nemo.nemo.api.album;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-B. 앨범 CRUD API E2E 테스트
 * TC-API-E2E-ALBUM-01 ~ 08
 */
@DisplayName("7-B Album CRUD API E2E")
class AlbumApiTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-API-E2E-ALBUM-01: 앨범 생성 → 200 + myRole=ADMIN")
    void album01_create_adminRole() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "[TC] 봄 여행"));
        var resp = post("/albums", aliceToken, body);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("id").asText()).isNotBlank();
        assertThat(json(resp).path("data").path("myRole").asText()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("TC-API-E2E-ALBUM-02: 앨범 생성 시 기본 페이지 자동 생성")
    void album02_create_defaultPageCreated() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 자동페이지");

        var resp = get("/albums/" + albumId + "/pages", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);

        var pageList = json(resp).path("data");
        assertThat(pageList.isArray()).isTrue();
        assertThat(pageList.size()).isGreaterThanOrEqualTo(1);
        assertThat(pageList.get(0).path("pageOrder").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-API-E2E-ALBUM-03: 빈 이름 → 400")
    void album03_emptyName_400() throws Exception {
        assertThat(statusOf(post("/albums", aliceToken, "{\"name\":\"\"}"))).isEqualTo(400);
        assertThat(statusOf(post("/albums", aliceToken, "{\"name\":\"   \"}"))).isEqualTo(400);
    }

    @Test
    @DisplayName("TC-API-E2E-ALBUM-04: 31자 이름 → 400 / 30자 → 200")
    void album04_nameLength_validation() throws Exception {
        String name31 = objectMapper.writeValueAsString(Map.of("name", "가".repeat(31)));
        assertThat(statusOf(post("/albums", aliceToken, name31))).isEqualTo(400);

        String name30 = objectMapper.writeValueAsString(Map.of("name", "가".repeat(30)));
        assertThat(statusOf(post("/albums", aliceToken, name30))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-API-E2E-ALBUM-05: GET /albums → owned/joined 분리")
    void album05_getAlbums_ownedJoinedSeparated() throws Exception {
        createAlbum(aliceToken, "[TC] 앨범A");

        var resp = get("/albums", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);

        var data = json(resp).path("data");
        assertThat(data.has("owned")).isTrue();
        assertThat(data.has("joined")).isTrue();
        assertThat(data.path("owned").isArray()).isTrue();
        assertThat(data.path("joined").isArray()).isTrue();
    }

    @Test
    @DisplayName("TC-API-E2E-ALBUM-06: ADMIN 이름 변경 → 200")
    void album06_adminRename_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 원래이름");

        String body = objectMapper.writeValueAsString(Map.of("name", "[TC] 새이름"));
        var resp = patch("/albums/" + albumId, aliceToken, body);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("name").asText()).isEqualTo("[TC] 새이름");
    }

    @Test
    @DisplayName("TC-API-E2E-ALBUM-07: EDITOR가 앨범 잠금 시도 → 403")
    void album07_editorLock_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 잠금테스트");

        String code = createInviteLink(aliceToken, albumId, "EDITOR", false);
        joinViaInvite(bobToken, code);

        String body = objectMapper.writeValueAsString(Map.of("isLocked", true));
        assertThat(statusOf(patch("/albums/" + albumId, bobToken, body))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-ALBUM-08: ADMIN 삭제 → 목록 제거 + 휴지통 이동")
    void album08_adminDelete_removedFromList_appearsInTrash() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 삭제앨범");

        assertThat(statusOf(delete("/albums/" + albumId, aliceToken))).isEqualTo(200);

        // owned에 없어야 함
        var owned = json(get("/albums", aliceToken)).path("data").path("owned");
        boolean found = false;
        for (var item : owned) {
            if (albumId.equals(item.path("id").asText())) { found = true; break; }
        }
        assertThat(found).isFalse();

        // 휴지통에 있어야 함
        var trashItems = json(get("/trash", aliceToken)).path("data");
        boolean inTrash = false;
        for (var item : trashItems) {
            if (albumId.equals(item.path("referenceId").asText())) { inTrash = true; break; }
        }
        assertThat(inTrash).isTrue();
    }
}
