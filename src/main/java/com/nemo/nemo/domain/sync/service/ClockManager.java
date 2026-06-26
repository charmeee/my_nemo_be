package com.nemo.nemo.domain.sync.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 페이지별 단조 증가 서버 클럭(시퀀스 번호).
 * - key: pageId, value: AtomicLong
 * - LWW 충돌 해결 및 클라이언트 delta hydration 기준점으로 사용
 * - DB 복구 시 initialize, 변경 적용 시 increment
 */
@Component
public class ClockManager {

    private final ConcurrentHashMap<String, AtomicLong> clocks = new ConcurrentHashMap<>();

    public long increment(String pageId) {
        return clocks.computeIfAbsent(pageId, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void initialize(String pageId, long initialClock) {
        clocks.put(pageId, new AtomicLong(initialClock));
    }

    public long get(String pageId) {
        return clocks.getOrDefault(pageId, new AtomicLong(0)).get();
    }

    public void remove(String pageId) {
        clocks.remove(pageId);
    }
}
