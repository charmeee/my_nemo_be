package com.nemo.nemo.domain.excalidraw.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Excalidraw element LWW (Last-Write-Wins) merge.
 * element.version (단조 증가) + element.versionNonce (tie-breaker) 기반.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElementDiffApplier {

    private final ObjectMapper objectMapper;

    /**
     * 서버 현재 elements JSON과 클라이언트가 push한 incoming elements를 LWW merge.
     *
     * @param serverElementsJson  서버 현재 상태 (ExcalidrawElement[] JSON)
     * @param incomingElementsJson 클라이언트가 push한 elements (변경/추가된 것만)
     * @return merge된 결과 JSON string
     */
    public String merge(String serverElementsJson, String incomingElementsJson) {
        try {
            // 서버 elements를 id → node Map으로 변환
            Map<String, JsonNode> serverMap = parseToMap(serverElementsJson);

            // incoming elements 순회하며 LWW 적용
            JsonNode incomingArr = objectMapper.readTree(incomingElementsJson);
            if (!incomingArr.isArray()) return serverElementsJson;

            for (JsonNode incoming : incomingArr) {
                String id = incoming.path("id").asText(null);
                if (id == null) continue;

                JsonNode current = serverMap.get(id);
                if (current == null) {
                    // 신규 element
                    serverMap.put(id, incoming);
                } else {
                    long incomingVersion = incoming.path("version").longValue(0);
                    long currentVersion = current.path("version").longValue(0);
                    long incomingNonce = incoming.path("versionNonce").longValue(0);
                    long currentNonce = current.path("versionNonce").longValue(0);

                    if (incomingVersion > currentVersion) {
                        serverMap.put(id, incoming);
                    } else if (incomingVersion == currentVersion && incomingNonce > currentNonce) {
                        serverMap.put(id, incoming);
                    }
                    // else: 서버가 더 최신 → 무시
                }
            }

            // Map → JSON array 직렬화
            ArrayNode result = objectMapper.createArrayNode();
            serverMap.values().forEach(result::add);
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            log.warn("[ElementDiffApplier] merge failed: {}", e.getMessage());
            return serverElementsJson;
        }
    }

    /**
     * elements JSON array를 id → JsonNode LinkedHashMap으로 파싱.
     * 순서 보존을 위해 LinkedHashMap 사용.
     */
    private Map<String, JsonNode> parseToMap(String elementsJson) throws Exception {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        if (elementsJson == null || elementsJson.isBlank() || "[]".equals(elementsJson.strip())) {
            return map;
        }
        JsonNode arr = objectMapper.readTree(elementsJson);
        if (arr.isArray()) {
            for (JsonNode node : arr) {
                String id = node.path("id").asText(null);
                if (id != null) map.put(id, node);
            }
        }
        return map;
    }
}
