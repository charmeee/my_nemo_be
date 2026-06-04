package com.nemo.nemo.domain.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DiffApplier {

    @SuppressWarnings("unchecked")
    public void apply(Map<String, Object> state, Map<String, Object> diff) {
        if (diff == null) return;
        for (Map.Entry<String, Object> entry : diff.entrySet()) {
            String key = entry.getKey();
            if (!(entry.getValue() instanceof List)) continue;
            List<Object> op = (List<Object>) entry.getValue();
            if (op.isEmpty()) continue;

            // TLDraw opTypes are strings: "put", "patch", "remove"/"delete"
            String opType = String.valueOf(op.get(0));

            switch (opType) {
                case "put" -> state.put(key, op.size() > 1 ? op.get(1) : null);
                case "patch" -> {
                    if (op.size() > 1) {
                        Object existing = state.get(key);
                        if (existing instanceof Map existingMap) {
                            ((Map<String, Object>) existingMap).putAll((Map<String, Object>) op.get(1));
                        } else {
                            state.put(key, op.get(1));
                        }
                    }
                }
                case "remove", "delete" -> state.remove(key);
                default -> log.warn("Unknown opType: {}", opType);
            }
        }
    }
}
