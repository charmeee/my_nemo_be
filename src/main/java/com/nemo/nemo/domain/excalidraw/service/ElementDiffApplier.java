package com.nemo.nemo.domain.excalidraw.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
     * 서버 현재 elements와 클라이언트 push elements를 LWW merge.
     * rebased=true: 서버 버전이 클라이언트보다 높아 클라이언트 변경이 무시된 경우 → 클라이언트는 큐를 재빌드해야 함.
     */
    public MergeResult merge(String serverElementsJson, String incomingElementsJson) {
        try {
            Map<String, JsonNode> serverMap = parseToMap(serverElementsJson);

            JsonNode incomingArr = objectMapper.readTree(incomingElementsJson);
            if (!incomingArr.isArray()) return new MergeResult(serverElementsJson, false);

            boolean rebased = false;
            for (JsonNode incoming : incomingArr) {
                String id = incoming.path("id").asText(null);
                if (id == null) continue;

                JsonNode current = serverMap.get(id);
                if (current == null) {
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
                    } else {
                        // 서버가 더 최신 → 클라이언트 변경 무시, rebase 필요
                        rebased = true;
                    }
                }
            }

            ArrayNode result = objectMapper.createArrayNode();
            serverMap.values().forEach(result::add);
            return new MergeResult(objectMapper.writeValueAsString(result), rebased);

        } catch (Exception e) {
            log.warn("[ElementDiffApplier] merge failed: {}", e.getMessage());
            return new MergeResult(serverElementsJson, false);
        }
    }

    /**
     * old → new 사이에서 변경된(추가되거나 version/nonce가 높아진) elements만 반환.
     * patch broadcast 시 전체 대신 diff만 전송해 대역폭을 절감합니다.
     */
    public List<JsonNode> getDiffElements(String oldElementsJson, String newElementsJson) {
        try {
            Map<String, JsonNode> oldMap = parseToMap(oldElementsJson);
            Map<String, JsonNode> newMap = parseToMap(newElementsJson);

            List<JsonNode> diff = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : newMap.entrySet()) {
                JsonNode oldNode = oldMap.get(entry.getKey());
                if (oldNode == null) {
                    diff.add(entry.getValue()); // 신규
                } else {
                    long newVer = entry.getValue().path("version").longValue(0);
                    long oldVer = oldNode.path("version").longValue(0);
                    long newNonce = entry.getValue().path("versionNonce").longValue(0);
                    long oldNonce = oldNode.path("versionNonce").longValue(0);
                    if (newVer > oldVer || (newVer == oldVer && newNonce != oldNonce)) {
                        diff.add(entry.getValue()); // 변경됨
                    }
                }
            }
            return diff;

        } catch (Exception e) {
            log.warn("[ElementDiffApplier] diff failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * N-CORE-11: non-deleted element 수 반환.
     */
    public ElementCountResult countNonDeleted(String elementsJson) throws Exception {
        JsonNode arr = objectMapper.readTree(elementsJson);
        if (!arr.isArray()) return new ElementCountResult(0);
        int count = 0;
        for (JsonNode node : arr) {
            if (!node.path("isDeleted").booleanValue(false)) {
                count++;
            }
        }
        return new ElementCountResult(count);
    }

    public record MergeResult(String elements, boolean rebased) {}

    public record ElementCountResult(int count) {}

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
