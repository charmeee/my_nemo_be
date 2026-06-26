package com.nemo.nemo.domain.sync.concurrency;

import com.nemo.nemo.domain.sync.service.RoomManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RoomManager - 동시성 테스트")
class RoomManagerConcurrencyTest {

    // RoomManager가 session.getId()를 decorator 키로 사용하므로 mock에 stable id를 부여한다.
    private static WebSocketSession mockSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        return session;
    }

    @Test
    @DisplayName("50개 세션 동시 join - 모두 등록")
    void join_50세션_동시성() throws InterruptedException {
        RoomManager roomManager = new RoomManager();
        int sessionCount = 50;
        List<WebSocketSession> sessions = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
            sessions.add(mockSession());
        }

        CountDownLatch latch = new CountDownLatch(sessionCount);
        ExecutorService executor = Executors.newFixedThreadPool(sessionCount);

        for (WebSocketSession session : sessions) {
            executor.submit(() -> {
                try {
                    roomManager.join("album-1", session);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(roomManager.size("album-1")).isEqualTo(sessionCount);
    }

    @Test
    @DisplayName("동시 join/leave - 최종 상태 일관성")
    void join_leave_동시성() throws InterruptedException {
        RoomManager roomManager = new RoomManager();
        int count = 30;
        List<WebSocketSession> sessions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sessions.add(mockSession());
        }

        // 먼저 모두 join
        for (WebSocketSession s : sessions) {
            roomManager.join("album-1", s);
        }

        // 절반은 leave 동시 실행
        CountDownLatch latch = new CountDownLatch(count / 2);
        ExecutorService executor = Executors.newFixedThreadPool(count / 2);

        for (int i = 0; i < count / 2; i++) {
            final WebSocketSession s = sessions.get(i);
            executor.submit(() -> {
                try {
                    roomManager.leave("album-1", s);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(roomManager.size("album-1")).isEqualTo(count / 2);
    }

    @Test
    @DisplayName("마지막 세션 leave 시 방 자동 제거")
    void 마지막_leave_방제거() {
        RoomManager roomManager = new RoomManager();
        WebSocketSession session = mockSession();

        roomManager.join("album-1", session);
        assertThat(roomManager.isEmpty("album-1")).isFalse();

        roomManager.leave("album-1", session);
        assertThat(roomManager.isEmpty("album-1")).isTrue();
    }
}
