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
