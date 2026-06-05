package com.nemo.nemo.domain.excalidraw.service;

import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.sync.service.ClockManager;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 페이지별 ExcalidrawElement[] 상태를 메모리(ConcurrentHashMap) + Redis + PostgreSQL 에 관리.
 * Write-Behind: push 수신 시 메모리+Redis 즉시 갱신, 5초 유휴 후 DB flush.
 * Spring-managed ThreadPoolTaskScheduler를 사용해 JVM 종료 시 graceful shutdown을 보장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PageDocumentStore {

    private static final String REDIS_KEY_PREFIX = "excalidraw:page:";

    private final StringRedisTemplate redis;
    private final ExcalidrawPageRepository repository;
    private final ObjectMapper objectMapper;
    private final ClockManager clockManager;
    private final ThreadPoolTaskScheduler taskScheduler;

    /** pageId → elements JSON (in-memory) */
    private final ConcurrentHashMap<String, String> pageStates = new ConcurrentHashMap<>();

    /** pageId → debounce timer for write-behind */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> flushTimers = new ConcurrentHashMap<>();

    /** 페이지 elements 로드 (메모리 → Redis → DB 순서) */
    public String loadElements(String pageId) {
        // 1. in-memory
        String cached = pageStates.get(pageId);
        if (cached != null) return cached;

        // 2. Redis
        String redisVal = redis.opsForValue().get(REDIS_KEY_PREFIX + pageId);
        if (redisVal != null) {
            pageStates.put(pageId, redisVal);
            return redisVal;
        }

        // 3. DB
        String elements = repository.findById(UUID.fromString(pageId))
                .map(p -> {
                    clockManager.initialize(pageId, p.getServerClock());
                    return p.getElements() != null ? p.getElements() : "[]";
                })
                .orElse("[]");

        pageStates.put(pageId, elements);
        redis.opsForValue().set(REDIS_KEY_PREFIX + pageId, elements);
        return elements;
    }

    /** elements를 LWW merge 후 저장 (메모리 + Redis 즉시, DB는 debounce 5초) */
    public String applyAndStore(String pageId, String mergedElementsJson, long serverClock) {
        pageStates.put(pageId, mergedElementsJson);
        redis.opsForValue().set(REDIS_KEY_PREFIX + pageId, mergedElementsJson);
        resetFlushTimer(pageId);
        return mergedElementsJson;
    }

    /** 마지막 세션 퇴장 시 즉시 DB flush */
    @Transactional
    public void flushToDbNow(String pageId) {
        cancelFlushTimer(pageId);
        doFlush(pageId);
    }

    /**
     * 앱 종료 시 메모리에 남아 있는 모든 페이지를 즉시 flush합니다.
     * 5초 debounce 도중 서버가 종료되어 변경이 유실되는 것을 방지합니다.
     */
    @PreDestroy
    public void flushAll() {
        int count = pageStates.size();
        pageStates.keySet().forEach(pageId -> {
            cancelFlushTimer(pageId);
            doFlush(pageId);
        });
        log.info("[PageDocumentStore] @PreDestroy flush complete: {} pages flushed", count);
    }

    private void resetFlushTimer(String pageId) {
        cancelFlushTimer(pageId);
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> doFlush(pageId),
                Instant.now().plusSeconds(5)
        );
        flushTimers.put(pageId, future);
    }

    private void cancelFlushTimer(String pageId) {
        ScheduledFuture<?> existing = flushTimers.remove(pageId);
        if (existing != null) existing.cancel(false);
    }

    private void doFlush(String pageId) {
        String elements = pageStates.get(pageId);
        if (elements == null) return;

        long clock = clockManager.get(pageId);
        try {
            repository.findById(UUID.fromString(pageId)).ifPresent(page -> {
                page.updateElements(elements, clock);
                repository.save(page);
                log.debug("[PageDocumentStore] flushed pageId={}, clock={}", pageId, clock);
            });
        } catch (Exception e) {
            log.warn("[PageDocumentStore] flush failed for pageId={}: {}", pageId, e.getMessage());
        }
    }

    public void evict(String pageId) {
        pageStates.remove(pageId);
        flushTimers.remove(pageId);
    }
}
