package com.nemo.nemo.domain.excalidraw.service;

import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.domain.excalidraw.entity.ExcalidrawPage;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.sync.service.ClockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 페이지별 ExcalidrawElement[] 상태를 메모리(ConcurrentHashMap) + Redis + PostgreSQL 에 관리.
 * Write-Behind: push 수신 시 메모리+Redis 즉시 갱신, 5초 유휴 후 DB flush.
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

    /** pageId → elements JSON (in-memory) */
    private final ConcurrentHashMap<String, String> pageStates = new ConcurrentHashMap<>();

    /** pageId → debounce timer for write-behind */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> flushTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

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

    private void resetFlushTimer(String pageId) {
        cancelFlushTimer(pageId);
        ScheduledFuture<?> future = scheduler.schedule(() -> doFlush(pageId), 5, TimeUnit.SECONDS);
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
