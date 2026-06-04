package com.nemo.nemo.domain.sync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DiffApplier - LWW 동기화 로직")
class DiffApplierTest {

    private DiffApplier diffApplier;

    @BeforeEach
    void setUp() {
        diffApplier = new DiffApplier();
    }

    @Nested
    @DisplayName("op=0 put: 레코드 전체 교체")
    class Put {
        @Test
        void 신규_키_삽입() {
            Map<String, Object> state = new HashMap<>();
            Map<String, Object> diff = Map.of("shape1", List.of(0, Map.of("type", "draw", "x", 10)));

            diffApplier.apply(state, diff);

            assertThat(state).containsKey("shape1");
            assertThat(((Map<?, ?>) state.get("shape1")).get("x")).isEqualTo(10);
        }

        @Test
        void 기존_키_완전_교체() {
            Map<String, Object> state = new HashMap<>();
            state.put("shape1", Map.of("x", 0, "y", 0));
            Map<String, Object> diff = Map.of("shape1", List.of(0, Map.of("x", 100)));

            diffApplier.apply(state, diff);

            Map<?, ?> result = (Map<?, ?>) state.get("shape1");
            assertThat(result.get("x")).isEqualTo(100);
            assertThat(result.containsKey("y")).isFalse(); // put은 전체 교체
        }
    }

    @Nested
    @DisplayName("op=1 patch: 부분 머지")
    class Patch {
        @Test
        void 기존_맵에_필드_추가() {
            Map<String, Object> existing = new HashMap<>(Map.of("x", 10, "y", 20));
            Map<String, Object> state = new HashMap<>(Map.of("shape1", existing));
            Map<String, Object> diff = Map.of("shape1", List.of(1, Map.of("color", "red")));

            diffApplier.apply(state, diff);

            Map<?, ?> result = (Map<?, ?>) state.get("shape1");
            assertThat(result.get("x")).isEqualTo(10);
            assertThat(result.get("y")).isEqualTo(20);
            assertThat(result.get("color")).isEqualTo("red");
        }

        @Test
        void 기존_맵에_필드_수정() {
            Map<String, Object> existing = new HashMap<>(Map.of("x", 10, "y", 20));
            Map<String, Object> state = new HashMap<>(Map.of("shape1", existing));
            Map<String, Object> diff = Map.of("shape1", List.of(1, Map.of("x", 99)));

            diffApplier.apply(state, diff);

            assertThat(((Map<?, ?>) state.get("shape1")).get("x")).isEqualTo(99);
            assertThat(((Map<?, ?>) state.get("shape1")).get("y")).isEqualTo(20);
        }

        @Test
        void 기존값이_맵이_아니면_교체() {
            Map<String, Object> state = new HashMap<>(Map.of("shape1", "primitive_value"));
            Map<String, Object> diff = Map.of("shape1", List.of(1, Map.of("x", 5)));

            diffApplier.apply(state, diff);

            assertThat(state.get("shape1")).isEqualTo(Map.of("x", 5));
        }
    }

    @Nested
    @DisplayName("op=2 remove: 레코드 삭제")
    class Remove {
        @Test
        void 존재하는_키_삭제() {
            Map<String, Object> state = new HashMap<>(Map.of("shape1", Map.of("x", 1)));
            Map<String, Object> diff = Map.of("shape1", List.of(2));

            diffApplier.apply(state, diff);

            assertThat(state).doesNotContainKey("shape1");
        }

        @Test
        void 없는_키_삭제는_무시() {
            Map<String, Object> state = new HashMap<>();
            Map<String, Object> diff = Map.of("nonexistent", List.of(2));

            diffApplier.apply(state, diff);

            assertThat(state).isEmpty();
        }
    }

    @Test
    @DisplayName("null diff는 무시")
    void nullDiff() {
        Map<String, Object> state = new HashMap<>(Map.of("shape1", "value"));
        diffApplier.apply(state, null);
        assertThat(state).containsKey("shape1");
    }

    @Test
    @DisplayName("LWW: 다수 레코드 동시 변경 - 각자 독립 보존")
    void lww_서로_다른_요소_동시_편집_모두_보존() {
        Map<String, Object> state = new HashMap<>();

        // 사용자A: shape1 추가
        Map<String, Object> diffA = Map.of("shape1", List.of(0, Map.of("type", "text", "content", "A")));
        // 사용자B: shape2 추가
        Map<String, Object> diffB = Map.of("shape2", List.of(0, Map.of("type", "draw", "content", "B")));

        diffApplier.apply(state, diffA);
        diffApplier.apply(state, diffB);

        assertThat(state).containsKeys("shape1", "shape2");
    }

    @Test
    @DisplayName("LWW: 같은 요소 충돌 - 나중 도착 기준 승리")
    void lww_같은_요소_충돌_나중_도착_승리() {
        Map<String, Object> state = new HashMap<>();

        Map<String, Object> diffEarly = Map.of("shape1", List.of(0, Map.of("x", 10)));
        Map<String, Object> diffLate  = Map.of("shape1", List.of(0, Map.of("x", 99)));

        diffApplier.apply(state, diffEarly);
        diffApplier.apply(state, diffLate); // 나중에 도착 → 승리

        assertThat(((Map<?, ?>) state.get("shape1")).get("x")).isEqualTo(99);
    }
}
