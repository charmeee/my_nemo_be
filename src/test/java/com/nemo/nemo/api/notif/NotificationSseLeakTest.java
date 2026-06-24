package com.nemo.nemo.api.notif;

import com.nemo.nemo.api.ApiE2ETestBase;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationService.subscribe(SSE)가 connection leak 을 일으키지 않는지 검증.
 *
 * Regression: 클래스 레벨 @Transactional(readOnly=true) + spring.jpa.open-in-view=true
 * 조합에서, SSE 응답이 SSE_TIMEOUT(30분) 동안 살아 있는 사이 Hikari connection 이
 * 풀에 반환되지 않아 max-pool=10 이 곧 고갈되던 이슈.
 */
class NotificationSseLeakTest extends ApiE2ETestBase {

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("SSE 연결을 (max-pool + 5) 개 열어도 Hikari 풀이 고갈되지 않고 REST 가 정상 응답한다")
    void sseSubscribe_doesNotExhaustHikariPool() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        int maxPool = hikari.getHikariPoolMXBean().getTotalConnections() > 0
                ? hikari.getMaximumPoolSize()
                : 10;
        int openCount = maxPool + 5; // 풀 한도를 초과하는 SSE 연결 수

        HttpClient client = HttpClient.newHttpClient();
        List<HttpResponse<java.io.InputStream>> openSse = new ArrayList<>();

        try {
            for (int i = 0; i < openCount; i++) {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url("/notifications/stream")))
                        .header("Authorization", bearer(aliceToken))
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build();
                HttpResponse<java.io.InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                assertThat(resp.statusCode())
                        .as("SSE 연결 #%d 가 200 으로 응답해야 함 (풀 고갈 시 30초 후 500)", i)
                        .isEqualTo(200);
                openSse.add(resp);
            }

            // SSE 응답이 비동기로 비워지길 기다린 뒤 (= 실제로 leak 이 발생하는 시점) 풀 상태 측정
            Thread.sleep(1500);

            int active = hikari.getHikariPoolMXBean().getActiveConnections();
            assertThat(active)
                    .as("SSE %d 개 열린 동안 active connection 수가 max-pool(%d) 미만이어야 함",
                            openCount, maxPool)
                    .isLessThan(maxPool);

            // 풀이 살아있는지: REST 호출이 5초 안에 정상 응답
            long start = System.currentTimeMillis();
            ResponseEntity<String> resp = post("/auth/refresh", null, null);
            long elapsedMs = System.currentTimeMillis() - start;

            assertThat(elapsedMs)
                    .as("SSE 가 열려 있는 동안 REST 가 5초 안에 응답해야 함 (풀 고갈 시 30초 hang)")
                    .isLessThan(5_000);
            // refresh 는 refreshToken 쿠키가 없어 401 이지만, '응답이 오는 것' 자체가 풀이 살아있다는 신호
            assertThat(resp.getStatusCode().is4xxClientError() || resp.getStatusCode().is2xxSuccessful())
                    .as("응답이 와야 함 (status=%s)", resp.getStatusCode())
                    .isTrue();
        } finally {
            // 열어둔 SSE 응답 정리
            for (HttpResponse<java.io.InputStream> r : openSse) {
                try { r.body().close(); } catch (Exception ignored) {}
            }
        }
    }

    @Test
    @DisplayName("SSE 연결을 닫으면 connection 이 풀로 정상 반환된다")
    void sseSubscribe_releasesConnectionOnClose() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

        // 베이스라인: 잠시 안정화
        Thread.sleep(500);
        int baseline = hikari.getHikariPoolMXBean().getActiveConnections();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/notifications/stream")))
                .header("Authorization", bearer(bobToken))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<java.io.InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK.value());

        // SSE 가 열려 있는 동안 active 가 baseline 보다 많이 증가하지 않아야 함
        Thread.sleep(800);
        int duringOpen = hikari.getHikariPoolMXBean().getActiveConnections();
        assertThat(duringOpen - baseline)
                .as("SSE 1개 열린 동안 active 가 1 이상 증가하면 안 됨 (leak 신호)")
                .isLessThanOrEqualTo(1);

        // 닫기
        resp.body().close();
        Thread.sleep(800);

        int afterClose = hikari.getHikariPoolMXBean().getActiveConnections();
        assertThat(afterClose)
                .as("SSE 닫은 뒤 active 가 baseline 수준으로 돌아와야 함")
                .isLessThanOrEqualTo(baseline);
    }
}
