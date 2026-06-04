package com.nemo.nemo.domain.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemo.nemo.domain.sync.entity.TLDrawDocument;
import com.nemo.nemo.domain.sync.repository.TLDrawDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentStore {

    private static final String KEY_PREFIX = "tldraw:doc:";

    private final StringRedisTemplate redis;
    private final TLDrawDocumentRepository repository;
    private final ObjectMapper objectMapper;
    private final ClockManager clockManager;
    private final DiffApplier diffApplier;

    @SuppressWarnings("unchecked")
    public Map<String, Object> load(String albumId) {
        String cached = redis.opsForValue().get(KEY_PREFIX + albumId);
        if (cached != null) {
            try {
                Map<String, Object> data = objectMapper.readValue(cached, Map.class);
                Number clock = (Number) data.getOrDefault("__serverClock", 0);
                clockManager.initialize(albumId, clock.longValue());
                data.remove("__serverClock");
                return data;
            } catch (Exception ignored) {}
        }

        TLDrawDocument doc = repository.findById(UUID.fromString(albumId))
                .orElseGet(() -> {
                    TLDrawDocument newDoc = TLDrawDocument.create(UUID.fromString(albumId));
                    return repository.save(newDoc);
                });

        clockManager.initialize(albumId, doc.getServerClock());

        try {
            Map<String, Object> state = objectMapper.readValue(doc.getState(), Map.class);
            saveToRedis(albumId, state);
            return state;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public void applyDiff(String albumId, Map<String, Object> state, Map<String, Object> diff) {
        diffApplier.apply(state, diff);
        saveToRedis(albumId, state);
    }

    private void saveToRedis(String albumId, Map<String, Object> state) {
        try {
            Map<String, Object> withClock = new HashMap<>(state);
            withClock.put("__serverClock", clockManager.get(albumId));
            redis.opsForValue().set(KEY_PREFIX + albumId, objectMapper.writeValueAsString(withClock));
        } catch (Exception ignored) {}
    }

    @Transactional
    public void flushToDb(String albumId) {
        String cached = redis.opsForValue().get(KEY_PREFIX + albumId);
        if (cached == null) return;

        try {
            Map<String, Object> data = objectMapper.readValue(cached, Map.class);
            Number clock = (Number) data.remove("__serverClock");
            String stateJson = objectMapper.writeValueAsString(data);
            long serverClock = clock != null ? clock.longValue() : 0;

            TLDrawDocument doc = repository.findById(UUID.fromString(albumId))
                    .orElseGet(() -> TLDrawDocument.create(UUID.fromString(albumId)));
            doc.updateState(stateJson, serverClock);
            repository.save(doc);

            redis.delete(KEY_PREFIX + albumId);
        } catch (Exception ignored) {}
    }

    public Map<String, Object> getState(String albumId) {
        return load(albumId);
    }
}
