package com.nemo.nemo.domain.excalidraw.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ElementDiffApplier - LWW merge")
class ElementDiffApplierTest {

    private ElementDiffApplier applier;

    @BeforeEach
    void setUp() {
        applier = new ElementDiffApplier(new ObjectMapper());
    }

    @Test
    @DisplayName("신규 element — merge 결과에 포함, rebased=false")
    void 신규_element_추가() {
        String server = "[]";
        String incoming = """
                [{"id":"a","version":1,"versionNonce":100,"isDeleted":false}]
                """;

        ElementDiffApplier.MergeResult result = applier.merge(server, incoming);

        assertThat(result.elements()).contains("\"id\":\"a\"");
        assertThat(result.rebased()).isFalse();
    }

    @Test
    @DisplayName("클라이언트 version 높음 — 클라이언트 승, rebased=false")
    void 클라이언트_version_승() {
        String server = """
                [{"id":"a","version":1,"versionNonce":100,"isDeleted":false}]
                """;
        String incoming = """
                [{"id":"a","version":2,"versionNonce":200,"isDeleted":false}]
                """;

        ElementDiffApplier.MergeResult result = applier.merge(server, incoming);

        assertThat(result.elements()).contains("\"version\":2");
        assertThat(result.rebased()).isFalse();
    }

    @Test
    @DisplayName("서버 version 높음 — 서버 승, rebased=true")
    void 서버_version_승_rebase() {
        String server = """
                [{"id":"a","version":3,"versionNonce":100,"isDeleted":false}]
                """;
        String incoming = """
                [{"id":"a","version":1,"versionNonce":200,"isDeleted":false}]
                """;

        ElementDiffApplier.MergeResult result = applier.merge(server, incoming);

        assertThat(result.elements()).contains("\"version\":3");
        assertThat(result.rebased()).isTrue();
    }

    @Test
    @DisplayName("version 동일, 클라이언트 nonce 높음 — 클라이언트 승, rebased=false")
    void 동일_version_클라이언트_nonce_승() {
        String server = """
                [{"id":"a","version":1,"versionNonce":100,"isDeleted":false}]
                """;
        String incoming = """
                [{"id":"a","version":1,"versionNonce":200,"isDeleted":false}]
                """;

        ElementDiffApplier.MergeResult result = applier.merge(server, incoming);

        assertThat(result.elements()).contains("\"versionNonce\":200");
        assertThat(result.rebased()).isFalse();
    }

    @Test
    @DisplayName("version 동일, 서버 nonce 높음 — 서버 승, rebased=true")
    void 동일_version_서버_nonce_승_rebase() {
        String server = """
                [{"id":"a","version":1,"versionNonce":999,"isDeleted":false}]
                """;
        String incoming = """
                [{"id":"a","version":1,"versionNonce":100,"isDeleted":false}]
                """;

        ElementDiffApplier.MergeResult result = applier.merge(server, incoming);

        assertThat(result.elements()).contains("\"versionNonce\":999");
        assertThat(result.rebased()).isTrue();
    }

    @Test
    @DisplayName("혼합 — 일부 서버 승 포함 시 rebased=true, 신규 element 포함")
    void 혼합_머지() {
        String server = """
                [
                  {"id":"a","version":5,"versionNonce":1,"isDeleted":false},
                  {"id":"b","version":1,"versionNonce":1,"isDeleted":false}
                ]
                """;
        String incoming = """
                [
                  {"id":"a","version":2,"versionNonce":9,"isDeleted":false},
                  {"id":"b","version":3,"versionNonce":1,"isDeleted":false},
                  {"id":"c","version":1,"versionNonce":1,"isDeleted":false}
                ]
                """;

        ElementDiffApplier.MergeResult result = applier.merge(server, incoming);

        assertThat(result.rebased()).isTrue();
        assertThat(result.elements()).contains("\"id\":\"c\"");
        assertThat(result.elements()).contains("\"version\":5"); // a: server wins
        assertThat(result.elements()).contains("\"version\":3"); // b: client wins
    }

    @Test
    @DisplayName("빈 incoming — 서버 상태 그대로, rebased=false")
    void 빈_incoming() {
        String server = """
                [{"id":"a","version":1,"versionNonce":1,"isDeleted":false}]
                """;

        ElementDiffApplier.MergeResult result = applier.merge(server, "[]");

        assertThat(result.elements()).contains("\"id\":\"a\"");
        assertThat(result.rebased()).isFalse();
    }

    @Test
    @DisplayName("countNonDeleted — isDeleted=true 제외")
    void countNonDeleted_삭제된_요소_제외() throws Exception {
        String elements = """
                [
                  {"id":"a","version":1,"isDeleted":false},
                  {"id":"b","version":1,"isDeleted":true},
                  {"id":"c","version":1,"isDeleted":false}
                ]
                """;

        ElementDiffApplier.ElementCountResult result = applier.countNonDeleted(elements);

        assertThat(result.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("countNonDeleted — 모두 삭제 시 0")
    void countNonDeleted_전체_삭제() throws Exception {
        String elements = """
                [
                  {"id":"a","version":1,"isDeleted":true},
                  {"id":"b","version":1,"isDeleted":true}
                ]
                """;

        assertThat(applier.countNonDeleted(elements).count()).isEqualTo(0);
    }

    @Test
    @DisplayName("getDiffElements — 변경된 element만 반환 (버전 증가 + 신규)")
    void getDiffElements_변경된것만() {
        String old = """
                [
                  {"id":"a","version":1,"versionNonce":1},
                  {"id":"b","version":1,"versionNonce":1}
                ]
                """;
        String updated = """
                [
                  {"id":"a","version":1,"versionNonce":1},
                  {"id":"b","version":2,"versionNonce":2},
                  {"id":"c","version":1,"versionNonce":1}
                ]
                """;

        List<JsonNode> diff = applier.getDiffElements(old, updated);

        assertThat(diff).hasSize(2);
        List<String> ids = diff.stream().map(n -> n.path("id").asText()).toList();
        assertThat(ids).containsExactlyInAnyOrder("b", "c");
    }

    @Test
    @DisplayName("getDiffElements — 변경 없으면 빈 리스트")
    void getDiffElements_변경없음() {
        String elements = """
                [{"id":"a","version":1,"versionNonce":1}]
                """;

        List<JsonNode> diff = applier.getDiffElements(elements, elements);

        assertThat(diff).isEmpty();
    }

    @Test
    @DisplayName("getDiffElements — isDeleted=true 원소도 diff에 포함")
    void getDiffElements_삭제된것도_포함() {
        String old = """
                [{"id":"a","version":1,"versionNonce":1,"isDeleted":false}]
                """;
        String updated = """
                [{"id":"a","version":2,"versionNonce":2,"isDeleted":true}]
                """;

        List<JsonNode> diff = applier.getDiffElements(old, updated);

        assertThat(diff).hasSize(1);
        assertThat(diff.get(0).path("isDeleted").booleanValue()).isTrue();
    }
}
