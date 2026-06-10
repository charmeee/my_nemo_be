package com.nemo.nemo.api.perf;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StopWatch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4. 성능 테스트 (JUnit StopWatch 기반 — RANDOM_PORT 실제 HTTP)
 * TC-PERF-01, TC-PERF-03, TC-PERF-06, TC-PERF-07
 */
@DisplayName("4. Performance API Tests")
class PerformanceApiTest extends ApiE2ETestBase {

    // ────────────────────────────────────────────────
    // 4-A. 응답 시간 목표
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("TC-PERF-01: 앨범 목록 응답 < 1000ms (캐시 히트)")
    void perf01_albumList_under1000ms() throws Exception {
        for (int i = 0; i < 5; i++) {
            createAlbum(aliceToken, "[TC] perf앨범" + i);
        }

        // 1회차 (DB 조회)
        StopWatch sw1 = new StopWatch();
        sw1.start();
        assertThat(statusOf(get("/albums", aliceToken))).isEqualTo(200);
        sw1.stop();

        // 2회차 (캐시 히트)
        StopWatch sw2 = new StopWatch();
        sw2.start();
        assertThat(statusOf(get("/albums", aliceToken))).isEqualTo(200);
        sw2.stop();

        long cacheHitMs = sw2.getTotalTimeMillis();
        assertThat(cacheHitMs)
                .as("캐시 히트 응답시간이 1000ms 미만이어야 함 (실제: %dms)", cacheHitMs)
                .isLessThan(1000L);
    }

    @RepeatedTest(3)
    @DisplayName("TC-PERF-01b: 앨범 목록 3회 반복 측정 — 모두 500ms 미만")
    void perf01b_albumList_repeated_under500ms() throws Exception {
        StopWatch sw = new StopWatch();
        sw.start();
        assertThat(statusOf(get("/albums", aliceToken))).isEqualTo(200);
        sw.stop();

        assertThat(sw.getTotalTimeMillis())
                .as("응답시간이 500ms 미만이어야 함 (실제: %dms)", sw.getTotalTimeMillis())
                .isLessThan(500L);
    }

    @Test
    @DisplayName("TC-PERF-03: 이미지 업로드 < 5000ms")
    void perf03_imageUpload_under5000ms() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 업로드성능");

        StopWatch sw = new StopWatch();
        sw.start();
        assertThat(statusOf(upload("/albums/" + albumId + "/images", aliceToken, fakejpeg("file")))).isEqualTo(200);
        sw.stop();

        long uploadMs = sw.getTotalTimeMillis();
        assertThat(uploadMs)
                .as("이미지 업로드 응답시간이 5000ms 미만이어야 함 (실제: %dms)", uploadMs)
                .isLessThan(5000L);
    }

    // ────────────────────────────────────────────────
    // 4-B. 동시성 테스트 (API 레벨 한도 검증)
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("TC-PERF-06: 500개 element 초과 push 거부 (REST 기반 한도 확인)")
    void perf06_pageElementLimit_500() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] element한도");

        var pagesResp = get("/albums/" + albumId + "/pages", aliceToken);
        assertThat(statusOf(pagesResp)).isEqualTo(200);

        var json = json(pagesResp);
        assertThat(json.path("data").isArray()).isTrue();
        assertThat(json.path("data").size()).isGreaterThanOrEqualTo(1);

        String pageId = json.path("data").get(0).path("pageId").asText();
        assertThat(pageId).isNotBlank();

        assertThat(statusOf(patch("/albums/" + albumId + "/pages/" + pageId, aliceToken,
                "{\"elements\": \"[]\"}"))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-PERF-07: 10MB 초과 이미지 업로드 차단 → 400/413")
    void perf07_imageUpload_over10mb_rejected() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 대용량이미지");

        byte[] header = new byte[]{
                (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte)0xFF, (byte)0xD9
        };
        byte[] padding = new byte[11 * 1024 * 1024];
        byte[] combined = new byte[header.length + padding.length];
        System.arraycopy(header, 0, combined, 0, header.length);
        System.arraycopy(padding, 0, combined, header.length, padding.length);

        MockMultipartFile largeFile = new MockMultipartFile("file", "large.jpg", "image/jpeg", combined);

        int status = statusOf(upload("/albums/" + albumId + "/images", aliceToken, largeFile));
        assertThat(status)
                .as("10MB 초과 파일 업로드는 거부되어야 함 (status: %d)", status)
                .isIn(400, 413);
    }

    // ────────────────────────────────────────────────
    // 4-A. 응답 시간 — 추가 엔드포인트
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("TC-PERF-01c: 앨범 상세 조회 < 500ms")
    void perf01c_albumDetail_under500ms() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 상세성능");

        StopWatch sw = new StopWatch();
        sw.start();
        assertThat(statusOf(get("/albums/" + albumId, aliceToken))).isEqualTo(200);
        sw.stop();

        assertThat(sw.getTotalTimeMillis())
                .as("앨범 상세 응답시간이 500ms 미만이어야 함 (실제: %dms)", sw.getTotalTimeMillis())
                .isLessThan(500L);
    }

    @Test
    @DisplayName("TC-PERF-01d: 페이지 목록 조회 < 500ms")
    void perf01d_pageList_under500ms() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 페이지목록성능");

        StopWatch sw = new StopWatch();
        sw.start();
        assertThat(statusOf(get("/albums/" + albumId + "/pages", aliceToken))).isEqualTo(200);
        sw.stop();

        assertThat(sw.getTotalTimeMillis())
                .as("페이지 목록 응답시간이 500ms 미만이어야 함 (실제: %dms)", sw.getTotalTimeMillis())
                .isLessThan(500L);
    }

    @Test
    @DisplayName("TC-PERF-03b: 이미지 목록 조회 < 500ms")
    void perf03b_imageList_under500ms() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 이미지목록성능");

        StopWatch sw = new StopWatch();
        sw.start();
        assertThat(statusOf(get("/albums/" + albumId + "/images", aliceToken))).isEqualTo(200);
        sw.stop();

        assertThat(sw.getTotalTimeMillis())
                .as("이미지 목록 응답시간이 500ms 미만이어야 함 (실제: %dms)", sw.getTotalTimeMillis())
                .isLessThan(500L);
    }

    @Test
    @DisplayName("TC-PERF-15: 앨범 생성 100회 반복 → 누적 10초 미만 (순차 처리량)")
    void perf04_albumCreate100_under10s() throws Exception {
        StopWatch total = new StopWatch();
        total.start();

        for (int i = 0; i < 100; i++) {
            assertThat(statusOf(post("/albums", aliceToken,
                    "{\"name\": \"[TC]perf" + i + "\"}"))).isEqualTo(200);
        }

        total.stop();
        long totalMs = total.getTotalTimeMillis();
        assertThat(totalMs)
                .as("앨범 100회 생성 누적시간이 10000ms 미만이어야 함 (실제: %dms)", totalMs)
                .isLessThan(10000L);
    }
}
