package com.nemo.nemo.api.image;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-F. 이미지 업로드 API E2E 테스트
 * TC-API-E2E-IMG-01 ~ 05
 */
@DisplayName("7-F Image API E2E")
class ImageApiTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-API-E2E-IMG-01: JPEG 업로드 → 200 + url 반환")
    void img01_jpegUpload_urlReturned() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 이미지업로드");

        var resp = upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file"));
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("url").asText()).isNotBlank();
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-02: 10MB 초과 파일 → 400 또는 413")
    void img02_oversizedFile_400() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 대용량업로드");

        byte[] bigFile = new byte[11 * 1024 * 1024];
        bigFile[0] = (byte)0xFF; bigFile[1] = (byte)0xD8;
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.jpg", "image/jpeg", bigFile);

        var resp = upload("/albums/" + albumId + "/images", aliceToken, largeFile);
        assertThat(statusOf(resp)).isIn(400, 413);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-03: 허용되지 않는 MIME 타입 → 400")
    void img03_invalidMimeType_400() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 잘못된MIME");

        MockMultipartFile textFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello world".getBytes());

        assertThat(statusOf(upload("/albums/" + albumId + "/images", aliceToken, textFile))).isEqualTo(400);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-04: VIEWER 업로드 시도 → 403")
    void img04_viewerUpload_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] VIEWER업로드차단");

        String code = createInviteLink(aliceToken, albumId, "VIEWER", false);
        joinViaInvite(carolToken, code);

        assertThat(statusOf(upload("/albums/" + albumId + "/images", carolToken, fakejpeg("file")))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-06: GET /albums/{id}/images — 업로드 후 리스트에 등장")
    void img06_listImages_returnsUploaded() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 이미지목록");
        upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file"));
        upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file"));

        var resp = get("/albums/" + albumId + "/images", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-07: GET images — 비멤버 → 403")
    void img07_listImages_nonMember_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 이미지비멤버");
        assertThat(statusOf(get("/albums/" + albumId + "/images", bobToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-08: 업로더 본인이 이미지 삭제 → 200")
    void img08_deleteOwnImage_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 본인삭제");
        var uploadResp = upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file"));
        String imageId = json(uploadResp).path("data").path("id").asText();

        assertThat(statusOf(delete("/albums/" + albumId + "/images/" + imageId, aliceToken))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-09: ADMIN이 타인 이미지 삭제 → 200")
    void img09_adminDeleteOthersImage_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] ADMIN타인삭제");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        var uploadResp = upload("/albums/" + albumId + "/images", bobToken, fakejpeg("file"));
        String imageId = json(uploadResp).path("data").path("id").asText();

        assertThat(statusOf(delete("/albums/" + albumId + "/images/" + imageId, aliceToken))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-10: 일반 EDITOR가 타인 이미지 삭제 → 403")
    void img10_editorDeleteOthersImage_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] EDITOR타인차단");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));
        joinViaInvite(carolToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Bob 업로드
        var uploadResp = upload("/albums/" + albumId + "/images", bobToken, fakejpeg("file"));
        String imageId = json(uploadResp).path("data").path("id").asText();

        // Carol(EDITOR)이 Bob 이미지 삭제 → 403
        assertThat(statusOf(delete("/albums/" + albumId + "/images/" + imageId, carolToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-11: 존재하지 않는 imageId 삭제 → 404")
    void img11_deleteNonExistentImage_404() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 없는이미지");
        String fakeImageId = java.util.UUID.randomUUID().toString();
        assertThat(statusOf(delete("/albums/" + albumId + "/images/" + fakeImageId, aliceToken))).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-12: 잠긴 앨범에 EDITOR 업로드 → 403 (ALBUM_LOCKED)")
    void img12_lockedAlbum_editorUpload_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 잠긴앨범업로드");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // 앨범 잠금
        patch("/albums/" + albumId, aliceToken, "{\"isLocked\":true}");

        // EDITOR(Bob) 업로드 시도 → 403
        assertThat(statusOf(upload("/albums/" + albumId + "/images", bobToken, fakejpeg("file")))).isEqualTo(403);

        // ADMIN(Alice)은 잠겨도 업로드 가능 → 200
        assertThat(statusOf(upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file")))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-API-E2E-IMG-05: 비멤버가 이미지 URL 접근 → 401/403")
    void img05_nonMemberAccess_401or403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 비멤버이미지");

        var uploadResp = upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file"));
        assertThat(statusOf(uploadResp)).isEqualTo(200);

        String imageUrl = json(uploadResp).path("data").path("url").asText();
        String path = imageUrl.contains("localhost")
                ? imageUrl.substring(imageUrl.indexOf("/files"))
                : imageUrl;

        // 토큰 없이 접근 → 401
        var noAuthResp = rest.getForEntity(url(path), String.class);
        assertThat(statusOf(noAuthResp)).isIn(401, 403);

        // Alice(멤버)는 접근 가능 → 200
        assertThat(statusOf(get(path, aliceToken))).isEqualTo(200);
    }
}
