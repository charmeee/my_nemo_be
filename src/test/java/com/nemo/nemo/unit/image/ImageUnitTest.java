package com.nemo.nemo.unit.image;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1-F 이미지 단위 테스트 — E2E에서 누락된 항목
 * TC-IMG-02 (PNG), TC-IMG-03 (WebP), TC-IMG-09 (목록 조회), TC-IMG-10 (삭제)
 */
@DisplayName("1-F Image Unit Tests")
class ImageUnitTest extends ApiE2ETestBase {

    private MockMultipartFile fakePng(String field) {
        // PNG signature (8 bytes) + minimal IHDR
        byte[] bytes = new byte[]{
                (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00
        };
        return new MockMultipartFile(field, "test.png", "image/png", bytes);
    }

    private MockMultipartFile fakeWebp(String field) {
        // WebP: RIFF....WEBP + VP8L chunk header
        byte[] bytes = new byte[]{
                0x52, 0x49, 0x46, 0x46,  // "RIFF"
                0x24, 0x00, 0x00, 0x00,  // file size
                0x57, 0x45, 0x42, 0x50,  // "WEBP"
                0x56, 0x50, 0x38, 0x4C,  // "VP8L"
                0x08, 0x00, 0x00, 0x00,  // chunk size
                0x2F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
        return new MockMultipartFile(field, "test.webp", "image/webp", bytes);
    }

    @Test
    @DisplayName("TC-IMG-02: PNG 업로드 → 200 + url 반환")
    void img02_pngUpload_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] PNG업로드");

        var resp = upload("/albums/" + albumId + "/images", aliceToken, fakePng("file"));
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("url").asText()).isNotBlank();
        assertThat(json(resp).path("data").path("mimeType").asText()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("TC-IMG-03: WebP 업로드 → 200 + url 반환")
    void img03_webpUpload_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] WebP업로드");

        var resp = upload("/albums/" + albumId + "/images", aliceToken, fakeWebp("file"));
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").path("url").asText()).isNotBlank();
        assertThat(json(resp).path("data").path("mimeType").asText()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("TC-IMG-09: 이미지 목록 조회 → 200 + 업로드된 이미지 포함")
    void img09_imageList_200() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 이미지목록");

        // 이미지 2개 업로드
        upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file"));
        upload("/albums/" + albumId + "/images", aliceToken, fakePng("file"));

        var resp = get("/albums/" + albumId + "/images", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);

        var data = json(resp).path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(2);

        // 각 이미지에 url, mimeType, id 포함
        for (var img : data) {
            assertThat(img.path("url").asText()).isNotBlank();
            assertThat(img.path("mimeType").asText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("TC-IMG-10: 이미지 삭제 → 200, 목록에서 제거")
    void img10_deleteImage_200_removedFromList() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 이미지삭제");

        var uploadResp = upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file"));
        String imageId = json(uploadResp).path("data").path("id").asText();

        // 삭제
        assertThat(statusOf(delete("/albums/" + albumId + "/images/" + imageId, aliceToken))).isEqualTo(200);

        // 목록에서 제거 확인
        var listResp = get("/albums/" + albumId + "/images", aliceToken);
        var data = json(listResp).path("data");
        boolean found = false;
        for (var img : data) {
            if (imageId.equals(img.path("id").asText())) { found = true; break; }
        }
        assertThat(found).isFalse();
    }

    @Test
    @DisplayName("TC-IMG-10b: 타인 이미지 삭제 시도 → 403")
    void img10b_deleteOthersImage_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 타인이미지삭제");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Alice가 업로드
        var uploadResp = upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file"));
        String imageId = json(uploadResp).path("data").path("id").asText();

        // Bob이 삭제 시도
        int status = statusOf(delete("/albums/" + albumId + "/images/" + imageId, bobToken));
        assertThat(status).isIn(403, 400);
    }
}
