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
                    clockManager.increment("album-1");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(clockManager.get("album-1")).isEqualTo(100L);
    }

    @Test
    @DisplayName("여러 앨범 동시 increment - 각 앨범별 독립성 보장")
    void 여러_앨범_동시_increment() throws InterruptedException {
        ClockManager clockManager = new ClockManager();
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    clockManager.increment("album-A");
                } finally {
                    latch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    clockManager.increment("album-B");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(clockManager.get("album-A")).isEqualTo(50L);
        assertThat(clockManager.get("album-B")).isEqualTo(50L);
    }
}
