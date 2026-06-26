package com.nemo.nemo.domain.sync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClockManager - 서버 시계 관리")
class ClockManagerTest {

    private ClockManager clockManager;

    @BeforeEach
    void setUp() {
        clockManager = new ClockManager();
    }

    @Test
    @DisplayName("초기화 전 get은 0을 반환")
    void get_미초기화_반환0() {
        assertThat(clockManager.get("page-1")).isEqualTo(0L);
    }

    @Test
    @DisplayName("increment는 1부터 단조 증가")
    void increment_단조증가() {
        assertThat(clockManager.increment("page-1")).isEqualTo(1L);
        assertThat(clockManager.increment("page-1")).isEqualTo(2L);
        assertThat(clockManager.increment("page-1")).isEqualTo(3L);
    }

    @Test
    @DisplayName("initialize로 초기값 설정 후 increment는 그다음 값")
    void initialize_후_increment() {
        clockManager.initialize("page-1", 10L);
        assertThat(clockManager.get("page-1")).isEqualTo(10L);
        assertThat(clockManager.increment("page-1")).isEqualTo(11L);
    }

    @Test
    @DisplayName("서로 다른 pageId는 독립된 시계")
    void 다른_페이지_독립_시계() {
        clockManager.increment("page-1");
        clockManager.increment("page-1");
        clockManager.increment("page-2");

        assertThat(clockManager.get("page-1")).isEqualTo(2L);
        assertThat(clockManager.get("page-2")).isEqualTo(1L);
    }

    @Test
    @DisplayName("remove 후 get은 다시 0을 반환")
    void remove_후_0반환() {
        clockManager.increment("page-1");
        clockManager.remove("page-1");
        assertThat(clockManager.get("page-1")).isEqualTo(0L);
    }
}
