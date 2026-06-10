package com.nemo.nemo.domain.excalidraw.service;

import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.sync.service.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * isDeleted:true tombstone 원소를 주기적으로 제거.
 * 조건: 해당 앨범에 활성 WS 세션이 없는 경우에만 처리 (세션이 있으면 다음 GC 주기까지 대기).
 * 매일 새벽 4시 05분 실행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TombstoneGCTask {

    private final ExcalidrawPageRepository pageRepository;
    private final PageDocumentStore pageDocumentStore;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 5 4 * * *")
    @Transactional
    public void runGC() {
        pageRepository.findAll().forEach(page -> {
            if (page.getDeletedAt() != null) return;

            String albumId = page.getAlbum().getId().toString();
            if (!roomManager.isEmpty(albumId)) return; // 활성 세션 있으면 스킵

            String elements = page.getElements();
            if (elements == null || elements.isBlank() || elements.equals("[]")) return;

            try {
                List<Object> list = objectMapper.readValue(elements, List.class);
                List<Object> cleaned = list.stream()
                        .filter(el -> {
                            if (el instanceof Map<?, ?> m) {
                                return !Boolean.TRUE.equals(m.get("isDeleted"));
                            }
                            return true;
                        })
                        .toList();

                int removed = list.size() - cleaned.size();
                if (removed > 0) {
                    String cleanedJson = objectMapper.writeValueAsString(cleaned);
                    page.updateElements(cleanedJson, page.getServerClock());
                    pageRepository.save(page);
                    pageDocumentStore.evict(page.getPageId().toString());
                    log.info("[TombstoneGC] pageId={} tombstone {}개 제거", page.getPageId(), removed);
                }
            } catch (Exception e) {
                log.warn("[TombstoneGC] pageId={} 처리 실패: {}", page.getPageId(), e.getMessage());
            }
        });
    }
}
