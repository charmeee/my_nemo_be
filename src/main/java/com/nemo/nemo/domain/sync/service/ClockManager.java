package com.nemo.nemo.domain.sync.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ClockManager {

    private final ConcurrentHashMap<String, AtomicLong> clocks = new ConcurrentHashMap<>();

    public long increment(String albumId) {
        return clocks.computeIfAbsent(albumId, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void initialize(String albumId, long initialClock) {
        clocks.put(albumId, new AtomicLong(initialClock));
    }

    public long get(String albumId) {
        return clocks.getOrDefault(albumId, new AtomicLong(0)).get();
    }

    public void remove(String albumId) {
        clocks.remove(albumId);
    }
}
