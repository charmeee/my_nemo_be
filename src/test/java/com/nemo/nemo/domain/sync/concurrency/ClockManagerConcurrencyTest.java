package com.nemo.nemo.domain.sync.concurrency;

import com.nemo.nemo.domain.sync.service.ClockManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClockManager - 동시성 테스트")
class ClockManagerConcurrencyTest {

    @Test
    @DisplayName("100개 스레드 동시 increment - 유실 없이 정확히 100")
    void increment_100스레드_동시성() throws InterruptedException {
        ClockManager clockManager = new ClockManager();
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    clockManager.increment("page-1");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(clockManager.get("page-1")).isEqualTo(100L);
    }

    @Test
    @DisplayName("여러 페이지 동시 increment - 각 페이지별 독립성 보장")
    void 여러_페이지_동시_increment() throws InterruptedException {
        ClockManager clockManager = new ClockManager();
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    clockManager.increment("page-A");
                } finally {
                    latch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    clockManager.increment("page-B");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(clockManager.get("page-A")).isEqualTo(50L);
        assertThat(clockManager.get("page-B")).isEqualTo(50L);
    }
}
